# FlyDrive CNS

Android 2D driving simulator controlled by a spiking model constrained by the **MaleCNS v1.0** adult male *Drosophila melanogaster* connectome released by HHMI Janelia FlyEM, Cambridge/MRC LMB collaborators and Google Research.

## What is real and what is modeled

The connectome wiring is anatomical data, not a ready-made software brain. The build script downloads the official MaleCNS v1.0 bulk files from `storage.googleapis.com/flyem-male-cns`, keeps traced non-glial neurons, preserves measured pre→post wiring, uses measured synapse counts and neuron-level predicted neurotransmitters, and records SHA-256 hashes in the APK metadata.

The following are **modeling choices made by FlyDrive CNS**: leaky-integrate-and-fire dynamics, fast excitatory/inhibitory sign rules, mapping the simulated road sensors to fly visual populations, mapping descending neurons to steering/braking, reward design, and evolutionary optimization of synaptic gain multipliers. Training does not add or delete connectome edges.

## Simulation

The world is generated continuously around the car. It has a curving multilane road, traffic, intersections and traffic lights. The agent receives lane/heading error, looming/collision threat and light state through biologically named sensory populations. Steering is read primarily from bilateral DNa01/DNa02 populations; DNp01/DNp09 contribute to braking/escape/freeze behavior.

Fitness rewards forward progress, lane keeping, stable heading and correct behavior around lights. It penalizes collisions, red-light violations, off-road driving and useless stopping. Training mutates per-cell-type gain values while the measured wiring remains fixed.

## Build

The GitHub Actions workflow downloads the official MaleCNS dataset, converts it into an Android-friendly memory-mapped sparse graph and assembles a debug APK. The resulting APK embeds `malecns_graph.bin`, `malecns_groups.json` and `malecns_meta.json`.

The original MaleCNS dataset is CC-BY. Preserve attribution when redistributing builds containing derived graph data.
