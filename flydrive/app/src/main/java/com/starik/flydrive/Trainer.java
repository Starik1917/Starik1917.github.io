package com.starik.flydrive;

import java.util.Random;

final class Trainer {
    private final BrainModel brain;
    private final Random rng = new Random(5601L);
    private float[] base;
    private float[] candidate;
    private float[] generationBest;
    private float generationBestScore = -Float.MAX_VALUE;
    private float candidateScore;
    private float episodeTime;
    private int candidateIndex;
    int generation;
    int populationSize = 7;
    float episodeSeconds = 14f;
    float mutationSigma = 0.18f;
    float lastFitness;
    float bestFitness = -Float.MAX_VALUE;

    Trainer(BrainModel b) {
        brain = b;
        base = b.bestTrainableVector();
        candidate = base.clone();
        generationBest = base.clone();
        brain.setCandidateFrom(candidate);
    }

    boolean addReward(float reward, float dt) {
        candidateScore += reward;
        episodeTime += dt;
        if (episodeTime < episodeSeconds) return false;
        finishCandidate();
        return true;
    }

    private void finishCandidate() {
        lastFitness = candidateScore;
        if (candidateScore > generationBestScore) {
            generationBestScore = candidateScore;
            generationBest = candidate.clone();
        }
        if (candidateScore > bestFitness) bestFitness = candidateScore;
        candidateIndex++;
        if (candidateIndex >= populationSize) {
            base = generationBest.clone();
            brain.commitTrainableVector(base);
            generation++;
            candidateIndex = 0;
            generationBestScore = -Float.MAX_VALUE;
        }
        candidate = mutate(base, candidateIndex == 0 ? 0f : mutationSigma);
        brain.setCandidateFrom(candidate);
        brain.resetActivity();
        candidateScore = 0f;
        episodeTime = 0f;
    }

    private float[] mutate(float[] src, float sigma) {
        float[] out = src.clone();
        if (sigma == 0f) return out;
        for (int i = 0; i < out.length; i++) {
            if (rng.nextFloat() < 0.30f) {
                double g = Math.exp(rng.nextGaussian() * sigma);
                out[i] = MathUtil.clamp((float)(out[i] * g), 0.15f, 6f);
            }
        }
        return out;
    }

    int candidateNumber() { return candidateIndex + 1; }
    float episodeProgress() { return MathUtil.clamp(episodeTime / episodeSeconds, 0f, 1f); }
}
