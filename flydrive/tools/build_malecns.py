#!/usr/bin/env python3
"""Build the Android sparse graph from the official MaleCNS v1.0 bulk files.

The connectome wiring is not invented or rewired. We keep traced non-glial neurons,
keep measured neuron->neuron connections with >= MIN_SYN synapses, and assign fast
connection sign from the neuron-level neurotransmitter prediction. The Android app
changes per-cell-type synaptic gains during training, but never adds/removes edges.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import struct
import urllib.request
from pathlib import Path

import numpy as np
import pandas as pd
import pyarrow as pa
import pyarrow.ipc as ipc

BASE = "https://storage.googleapis.com/flyem-male-cns/v1.0/connectome-data/flat-connectome"
FILES = {
    "annotations": f"{BASE}/body-annotations-male-cns-v1.0-minconf-0.5.feather",
    "neurotransmitters": f"{BASE}/body-neurotransmitters-male-cns-v1.0.feather",
    "weights": f"{BASE}/connectome-weights-male-cns-v1.0-minconf-0.5.feather",
}
MIN_SYN = 3
SIGN = {
    "acetylcholine": 1, "gaba": -1, "glutamate": -1, "histamine": -1,
    "dopamine": 0, "octopamine": 0, "serotonin": 0,
    "unclear": 0, "unknown": 0, "": 0,
}


def download(url: str, dst: Path) -> None:
    if dst.exists() and dst.stat().st_size > 1024:
        print(f"cache: {dst.name} ({dst.stat().st_size/1e6:.1f} MB)")
        return
    dst.parent.mkdir(parents=True, exist_ok=True)
    tmp = dst.with_suffix(dst.suffix + ".part")
    print(f"download: {url}")
    req = urllib.request.Request(url, headers={"User-Agent": "FlyDriveCNS/0.1"})
    with urllib.request.urlopen(req, timeout=120) as r, open(tmp, "wb") as f:
        total = int(r.headers.get("Content-Length") or 0); got = 0
        while True:
            b = r.read(8 * 1024 * 1024)
            if not b: break
            f.write(b); got += len(b)
            if total:
                print(f"  {got/total*100:5.1f}% ({got/1e6:.0f}/{total/1e6:.0f} MB)", flush=True)
    tmp.replace(dst)


def sha256(path: Path) -> str:
    h = hashlib.sha256()
    with open(path, "rb") as f:
        for b in iter(lambda: f.read(8 * 1024 * 1024), b""): h.update(b)
    return h.hexdigest()


def read_feather_batches(path: Path):
    source = pa.memory_map(str(path), "r")
    reader = ipc.open_file(source)
    for i in range(reader.num_record_batches): yield reader.get_batch(i)


def map_bodies(bodies: np.ndarray, ids: np.ndarray):
    pos = np.searchsorted(bodies, ids)
    ok = pos < len(bodies)
    exact = np.zeros_like(ok, dtype=bool)
    vi = np.nonzero(ok)[0]
    if len(vi): exact[vi] = bodies[pos[vi]] == ids[vi]
    return pos.astype(np.int32, copy=False), exact


def series_or_blank(df: pd.DataFrame, name: str) -> pd.Series:
    if name in df.columns: return df[name].fillna("").astype(str)
    return pd.Series([""] * len(df), index=df.index, dtype="object")


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--cache", default=".cache/malecns")
    ap.add_argument("--assets", default="app/src/main/assets")
    args = ap.parse_args(); cache = Path(args.cache); assets = Path(args.assets)
    cache.mkdir(parents=True, exist_ok=True); assets.mkdir(parents=True, exist_ok=True)

    local = {}
    for key, url in FILES.items():
        p = cache / url.rsplit("/", 1)[-1]; download(url, p); local[key] = p

    print("load annotations")
    ann = pd.read_feather(local["annotations"])
    if "bodyId" not in ann.columns: raise RuntimeError(f"unexpected annotation columns: {list(ann.columns)[:20]}")
    status = series_or_blank(ann, "status"); status_label = series_or_blank(ann, "statusLabel")
    traced = (status == "Traced") & (status_label.str.lower() != "glia")
    ann = ann.loc[traced].copy(); ann["bodyId"] = ann["bodyId"].astype(np.int64)
    ann = ann.drop_duplicates("bodyId").sort_values("bodyId")
    t = series_or_blank(ann, "type")
    for fallback in ("flywireType", "instance"):
        fb = series_or_blank(ann, fallback); t = t.mask(t == "", fb)
    ann["_type"] = t.fillna("").astype(str); ann["_instance"] = series_or_blank(ann, "instance")
    bodies = ann["bodyId"].to_numpy(np.int64); n = len(bodies)
    print(f"traced neurons: {n:,}")
    codes, type_names = pd.factorize(ann["_type"], sort=True); codes = codes.astype(np.uint16)
    type_names = [str(x) for x in type_names]; ntypes = len(type_names)
    if ntypes >= 65535: raise RuntimeError(f"too many type codes for uint16: {ntypes}")

    print("load neurotransmitters")
    nt = pd.read_feather(local["neurotransmitters"])
    body_col = "body" if "body" in nt.columns else "bodyId"
    nt_col = "consensus_nt" if "consensus_nt" in nt.columns else "consensusNt"
    nt = nt[[body_col, nt_col]].dropna(subset=[body_col]).drop_duplicates(body_col)
    nt_map = dict(zip(nt[body_col].astype(np.int64), nt[nt_col].fillna("unknown").astype(str).str.lower()))
    signs = np.fromiter((SIGN.get(nt_map.get(int(b), "unknown"), 0) for b in bodies), dtype=np.int8, count=n)
    print(f"signs: +{np.count_nonzero(signs>0):,}  -{np.count_nonzero(signs<0):,}  0={np.count_nonzero(signs==0):,}")

    print("stream/filter connectivity")
    pre_parts=[]; post_parts=[]; syn_parts=[]; kept_pairs=0
    for bi, batch in enumerate(read_feather_batches(local["weights"])):
        names=batch.schema.names
        if not {"body_pre","body_post","weight"}.issubset(names): raise RuntimeError(f"unexpected weights columns: {names}")
        w=batch.column(names.index("weight")).to_numpy(zero_copy_only=False); mask=w>=MIN_SYN
        if not np.any(mask): continue
        pre=batch.column(names.index("body_pre")).to_numpy(zero_copy_only=False)[mask].astype(np.int64,copy=False)
        post=batch.column(names.index("body_post")).to_numpy(zero_copy_only=False)[mask].astype(np.int64,copy=False)
        ww=w[mask].astype(np.int64,copy=False)
        ipre,ok1=map_bodies(bodies,pre); ipost,ok2=map_bodies(bodies,post); ok=ok1&ok2
        if np.any(ok):
            ipre=ipre[ok]; ipost=ipost[ok]; ww=ww[ok]; nz=signs[ipre]!=0
            ipre=ipre[nz]; ipost=ipost[nz]; ww=ww[nz]
            if len(ipre):
                pre_parts.append(ipre.astype(np.int32,copy=False)); post_parts.append(ipost.astype(np.int32,copy=False))
                syn_parts.append(np.clip(ww,1,65535).astype(np.uint16,copy=False)); kept_pairs+=len(ipre)
        print(f"  batch {bi+1}: kept total {kept_pairs:,}",flush=True)
    if not pre_parts: raise RuntimeError("no connectivity survived filtering")
    pre=np.concatenate(pre_parts); post=np.concatenate(post_parts); syn=np.concatenate(syn_parts)
    print(f"sorting {len(pre):,} signed neuron->neuron edges")
    order=np.argsort(pre,kind="stable"); pre=pre[order]; post=post[order]; syn=syn[order]
    counts=np.bincount(pre,minlength=n).astype(np.int64); indptr64=np.empty(n+1,dtype=np.int64); indptr64[0]=0
    np.cumsum(counts,out=indptr64[1:])
    if indptr64[-1]>=2**31: raise RuntimeError("edge count exceeds int32 CSR")
    indptr=indptr64.astype(np.int32); m=len(post)

    typ=ann["_type"].astype(str); inst=ann["_instance"].astype(str); combo=(typ+" "+inst).str.lower()
    def group(prefixes,side=None):
        mask=np.zeros(n,dtype=bool)
        for pr in prefixes: mask |= combo.str.contains(pr.lower(),regex=False).to_numpy()
        if side=="L": mask &= (combo.str.contains("_l",regex=False).to_numpy()|combo.str.contains(" left",regex=False).to_numpy())
        elif side=="R": mask &= (combo.str.contains("_r",regex=False).to_numpy()|combo.str.contains(" right",regex=False).to_numpy())
        return np.nonzero(mask)[0].astype(np.int32)
    groups={
        "motion_a":group(["T4a","T5a"]),"motion_b":group(["T4b","T5b"]),
        "motion_c":group(["T4c","T5c"]),"motion_d":group(["T4d","T5d"]),
        "looming":group(["LPLC2","LC4"]),"visual_color":group(["R7","R8","Dm8","Tm5c"]),
        "DNa01_L":group(["DNa01"],"L"),"DNa01_R":group(["DNa01"],"R"),
        "DNa02_L":group(["DNa02"],"L"),"DNa02_R":group(["DNa02"],"R"),
        "DNp01":group(["DNp01"]),"DNp09":group(["DNp09"]),
    }
    for base in ("DNa01","DNa02"):
        lk,rk=base+"_L",base+"_R"
        if len(groups[lk])==0 or len(groups[rk])==0:
            allg=group([base])
            if len(allg)>=2: groups[lk]=allg[::2]; groups[rk]=allg[1::2]
    motion=[groups[k] for k in ("motion_a","motion_b","motion_c","motion_d") if len(groups[k])]
    visual_union=np.unique(np.concatenate(motion)) if motion else np.arange(min(n,64),dtype=np.int32)
    for j,k in enumerate(("motion_a","motion_b","motion_c","motion_d")):
        if len(groups[k])==0: groups[k]=visual_union[j::4]
    if len(groups["looming"])==0: groups["looming"]=visual_union[:min(96,len(visual_union))]
    if len(groups["visual_color"])==0: groups["visual_color"]=visual_union[-min(96,len(visual_union)):]

    focus_nodes=np.zeros(n,dtype=bool)
    for arr in groups.values(): focus_nodes[arr]=True
    incident=focus_nodes[pre]|focus_nodes[post]; score=np.zeros(ntypes,dtype=np.float64)
    if np.any(incident):
        eidx=np.nonzero(incident)[0]
        np.add.at(score,codes[pre[eidx]].astype(np.int32),syn[eidx].astype(np.float64))
        np.add.at(score,codes[post[eidx]].astype(np.int32),syn[eidx].astype(np.float64))
    focus_types=np.unique(codes[focus_nodes].astype(np.int32)); score[focus_types]+=score.max(initial=1.0)+1.0
    top=np.argsort(score)[::-1]; top=top[score[top]>0][:384].astype(np.int32)
    if len(top)==0: top=focus_types[:384]
    groups["trainable_type_codes"]=top; groups["trainable_type_names"]=[type_names[int(i)] for i in top]

    graph=assets/"malecns_graph.bin"; print(f"write {graph}")
    with open(graph,"wb") as f:
        f.write(struct.pack("<8sIIII",b"FLYCNS10",n,m,ntypes,MIN_SYN))
        indptr.astype("<i4",copy=False).tofile(f); post.astype("<i4",copy=False).tofile(f)
        syn.astype("<u2",copy=False).tofile(f); codes.astype("<u2",copy=False).tofile(f); signs.astype("i1",copy=False).tofile(f)
    json_groups={k:([int(x) for x in v.tolist()] if isinstance(v,np.ndarray) else v) for k,v in groups.items()}
    (assets/"malecns_groups.json").write_text(json.dumps(json_groups,separators=(",",":")),encoding="utf-8")
    meta={
        "dataset":"MaleCNS v1.0","license":"CC-BY",
        "project":"HHMI Janelia FlyEM / Cambridge / MRC LMB / Google Research",
        "neurons":int(n),"edges":int(m),"min_synapses_per_pair":MIN_SYN,"type_count":int(ntypes),
        "wiring_modified":False,
        "fast_sign_rule":"ACh +; GABA/glutamate/histamine -; modulatory/unknown omitted from fast LIF propagation",
        "sources":FILES,"source_sha256":{k:sha256(v) for k,v in local.items()},"graph_sha256":sha256(graph),
        "note":"The connectome is anatomy. LIF dynamics, sensor-to-neuron mapping, readout mapping, reward, and gain training are modeling choices in FlyDriveCNS."
    }
    (assets/"malecns_meta.json").write_text(json.dumps(meta,indent=2),encoding="utf-8")
    print(json.dumps({k:meta[k] for k in ("neurons","edges","type_count","graph_sha256")},indent=2))
    print(f"graph size: {graph.stat().st_size/1e6:.1f} MB")
    return 0

if __name__=="__main__": raise SystemExit(main())
