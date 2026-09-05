# Feedback Round 1 — r/AynThor launch thread (Sept 5, 2026)

Source: [launch thread](https://www.reddit.com/r/AynThor/comments/1w7pl20/metroid_prime_hunters_recomp_is_now_available_for/)
(340 upvotes, 31K views) and [GitHub issue #1](https://github.com/aabrole/HuntersRecomp/issues/1).
Release under test: v0.2.3. Every reporter is credited in the README.

## Bugs

| # | Report | Reporter | Device | Status / hypothesis |
|---|--------|----------|--------|---------------------|
| B1 | No Android immersive fullscreen; nav bar overlays the game | u/Giodude12 | Thor (nav buttons on) | **Fixed in v0.2.4** (immersive-sticky on the game activity) |
| B2 | Inputs dead until the top screen is tapped; "can't shoot", stuck at Celestial Archives door | u/chur-bo-baggins (diagnosed by u/treesdotcom) | Thor Max | Root cause identified: Prime controls engage behind the relative-mouse "click to capture" gate; nothing auto-captures on Android. Fix: auto-capture at boot on Android |
| B3 | Buttons "mapped incorrectly vs documented" | u/chur-bo-baggins | Thor Max | Likely same root as B2: uncaptured mode runs the plain D-pad/button fallback instead of the Prime scheme |
| B4 | Menu accept/OK not bound | u/Giodude12 | Thor | Same capture-gate suspicion; verify Y=UI-OK works pre- and post-capture |
| B5 | Bottom screen stretched to the panel's aspect | u/Giodude12 | Thor | Real: the second-screen blit scales 256x192 to the full 1240x1080 panel. Fix: aspect-correct letterbox in the presenter |
| B6 | Audio crunch/stutter everywhere; better at 2x but persists | u/HighFivePondaBaba | Thor Max | Perf-linked but not only perf; needs audio-queue investigation (SDL2 callback ring, underrun accounting) |
| B7 | Post-save fps collapse + audio stutter after re-entering from ship | u/Playtimegoofball | Thor 12GB | Prime suspect: save-flush behavior after first flash write (possible repeated 256 KiB rewrites). High-value fix |
| B8 | Resolution scaling appears to do nothing | u/Giodude12 | Thor | Needs diagnostics: either compute-renderer fallback to software on that unit, or judged during FMV/2D content which internal res does not affect. Action: surface active renderer + res in the settings screen |
| B9 | Black screen entering morph ball and during cutscenes | u/Giodude12 | Thor | Repro needed; possibly the direct-present 2D frame path on some units |
| B10 | Intermittent right-stick aim latency after move-then-look | u/Giodude12 | Thor | Suspect the pad-aim engage/idle-out cycle (45-frame idle disengage re-establishes touch on re-engage). Tune or hold engagement |

## Feature requests

| Request | Requesters |
|---|---|
| RetroAchievements support | u/Xion_Stellar, u/Am3n |
| Rumble (DS Rumble Pak events to the Thor's motor) | u/Luna_the_Miqo |
| Gyro aiming | u/Chompsky___Honk, u/FyrusCarmin |
| Online multiplayer (future) | u/JTiberius21, u/galaxywalaxyz |

## Positive reports

- u/Luna_the_Miqo (Thor): "performs great, controls are a godsend" — running well in-game
- u/arnar62, u/Eyerone, u/Alexan_Hirdriel, u/LeSpermReceiver, u/galaxywalaxyz,
  u/marshmallown, u/HuttStuff_Here, u/blaster915, u/Rekusu7991, u/JTiberius21,
  u/Gearheadjunky — encouragement, nostalgia, and watchful waiting

## Patterns

- Device variance is the theme: smooth on Thor Pro (developer unit); most severe
  reports are Thor Max and base Thor. Need per-unit diagnostics before
  per-unit fixes.
- The B2 capture gate likely masquerades as several "broken controls" reports.
- Known-as-intended: intro FMVs are choppy/blurry (interpreter-heavy upstream
  path); skip with START or fast-forward with SELECT.

## Patch order (v0.2.4)

1. B1 immersive fullscreen (done)
2. B2/B3/B4 input auto-capture on Android
3. B5 bottom-screen aspect letterbox
4. B7 save-flush investigation
5. B8 renderer/res diagnostics surfaced in the settings screen
6. B6 audio-queue work
7. B9/B10 repro with reporters

## Performance investigation (Sept 5, evening) — measured on the dev Thor Pro

Method: the runner's per-second diagnostics (fps, emulation ms/frame, audio
underruns, interpreter instructions, dispatch counters) over the same
90-second boot/FMV/attract window each round, which is the game's worst content.

| Round | Change | fps avg | emu ms avg / max | underruns | interpreted instr/s |
|---|---|---|---|---|---|
| Baseline (v0.2.3/v0.2.4 engine) | | 37.4 | 28.6 / 46.4 | 2,019 | 7.5M |
| 1 | ARM9 ITCM alias bank (game copies arm9.bin code into ITCM; no bank declared there) | 45.8 | 20.3 / 28.7 | 1,249 | 5.0M |
| 2 | + 15 device-captured runtime banks (overlay code generations) | 53.7 | 15.2 / 27.9 | 639 | 766K |
| 5 | + every Tier-3 root as a seed (WRONG: split straight-line code per instruction, 5.7M fallthrough dispatches/s) | 50.4 | 17.5 / 26.6 | 926 | 151K |
| 6 | roots reduced to run heads (first address of each contiguous interpreted run) + audio: 125 ms runway, soft underruns | 58.3 | 13.5 / 16.4 | 248 | 199K |

Findings
- The audio crunch was CPU under-speed: emulation over the 16.6 ms frame budget starves the SDL queue, and each starvation was hard zero-filled (a click). The GPU was never the bottleneck (present < 2.5 ms).
- Post-save fps collapse (u/Playtimegoofball): the cartridge save was flushed synchronously, 256 KiB per SPI transaction, on the emulation thread. Now debounced on Android.
- Remaining budget: ~3.5 ms/frame of the emulation time is the emu thread waiting on the compute renderer's sync at 4x internal resolution (profile_totals.gpu3d_compute_sync_ns). 3x is the recommended setting until that sync is pipelined.

Reproducibility: alias/entry-point seeds are addresses only (coverage/*.json in the bank generator); runtime bank images come from the player's own session and are not committed.

| 7 | + run-head roots inside the runtime-swapped ITCM page (last hot spot) | 58.3 | 13.4 / 16.3 | 227 | 636 |
| 8 | emu thread on cpu5-7 only, sustained-performance mode removed (neutral) | 58.5 | 12.3 / 15.7 | 170 | ~600 |

3x vs 4x on the same window (round 7 engine): 3x halves the remaining underruns
(227 -> 110) and cuts the compute-renderer sync from ~3.5 ms to ~1 ms per frame.
On the attract-loop gameplay demos at 3x: 59.3 fps average, ~3 underruns/s,
emulation 13.9 ms/frame average with bursts to 17 ms. The interpreter is gone
(600 instr/s); what remains is the recompiled CPU work plus ~2.5 ms/frame of
synchronous present on the emulation thread. Next engineering item for a true
locked 60: move present/second-screen blit off the emulation thread.
Recommended setting on Thor: 3x internal resolution (the app default).
