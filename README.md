# Player Characters

A client-side Fabric mod for **Minecraft 1.21.11**. It shows players on a server as characters you choose: 3D
characters (the mod comes with an Alien, a Robot, a Frog and a Burger) or skins you make yourself. The character's
mouth opens while that player talks in [Simple Voice Chat](https://modrinth.com/plugin/simple-voice-chat), and when
someone says "fart", their character bends over and farts.

While a player is shown as a character, you don't see their armor, but you still see their name tag and the item
they're holding.

| Robot talking | Frog talking | Burger talking (its top bun is the mouth) |
|---|---|---|
| ![Robot](screenshots/robot-talking.png) | ![Frog](screenshots/frog-talking.png) | ![Burger](screenshots/burger-talking.png) |

| Saying "fart" | | |
|---|---|---|
| ![Burger fart](screenshots/fart-burger.png) | ![Robot fart](screenshots/fart-robot.png) | ![Alien fart](screenshots/fart-alien.png) |

| Alien from the side, holding a sword | Normal player in armor | Same player as a skin character (armor hidden) |
|---|---|---|
| ![Alien side](screenshots/alien-side.png) | ![Armor](screenshots/armor-normal.png) | ![Armor hidden](screenshots/armor-hidden.png) |

| Character menu (K) | Choosing a character | Key binds |
|---|---|---|
| ![Menu](screenshots/menu.png) | ![Picker](screenshots/picker.png) | ![Key binds](screenshots/key-binds.png) |

## Install

1. Install [Fabric Loader](https://fabricmc.net/use/) 0.20.1 or newer for Minecraft 1.21.11.
2. Put these in your `.minecraft/mods` folder:
   - `player-characters-3.0.0.jar` (this mod), from the [Releases page](https://github.com/itamarbleiberg/buti1/releases)
   - [Fabric API](https://modrinth.com/mod/fabric-api) for 1.21.11
   - [Simple Voice Chat](https://modrinth.com/plugin/simple-voice-chat) for Fabric 1.21.11 (needed for talking mouths
     and for saying "fart"; the server must have Simple Voice Chat too, as usual)

The server does **not** need this mod. Characters are only shown on your screen: other players keep seeing
normal players unless they install the mod and set it up themselves.

The jar is about 55 MB because it contains an offline speech recognizer (see [Farts](#farts)).

## Use

| Key (default) | What it does |
|---|---|
| **K** | Open the character menu |
| **U** | Turn talking mouths on/off |

Change the keys in **Options > Controls > Key Binds > Player Characters**.

In the character menu:

- Every player on the server is listed (you are first). Click the button next to a player to pick their
  character. The picker shows your own player as the highlighted character, with its mouth opening and closing.
- **Default** decides what everyone you haven't picked for looks like: their own skin, a random character
  (each player always gets the same one), or one specific character.
- **Talking mouths** does the same as the U key.
- **Farts** turns farting on or off.
- **Characters folder** opens the folder with your characters. **Reload** loads new or changed files.

Your choices are saved in `.minecraft/config/playercharacters.json`.

## Farts

When a player says "fart" in voice chat, their character bends over, a green cloud puffs out of its butt and you
hear a fart. This works for every character and for players shown in their own skin. Your own microphone counts too:
you see your character fart in third person, and other players who have the mod see it when they hear you. Typing
"fart" in chat does the same.

- The speech recognition is [Vosk](https://alphacephei.com/vosk/) with its small English model. It runs on your
  computer: no audio is sent anywhere, and it only hears the voices that Simple Voice Chat already plays for you.
- It listens for one word. The recognizer only knows "fart" and some words that sound like it ("far", "part",
  "heart", "farm"...), so those aren't taken for "fart". In a test with 108 computer-voiced sentences in six English
  accents, it caught "fart" in 32 of 36 and was fooled by 3 of the 72 sentences with lookalike words. Real voices
  and noisy microphones may do worse.
- The first time someone talks, the model is unpacked to `.minecraft/playercharacters/speech-model` (47 MB). It
  uses about 90 MB of memory while it's on, plus about 3 MB per person talking. Turning **Farts** off frees it.
- Vosk works on Windows (64-bit), macOS and Linux (64-bit). Where it can't start, the menu says "Fart detection
  unavailable" and typing "fart" still works.

## Characters

Characters live in `.minecraft/config/playercharacters/characters/`. The mod puts four 3D examples there (Alien,
Robot, Frog and Burger) and a `README.txt` with the details below.

Upgrading from version 2: the old skin Robot and Frog are replaced by the 3D ones, and the Alien is updated, unless
you changed those files. Changed files are kept. If a skin and a 3D folder have the same name, the 3D one is used and
the menu tells you which file was skipped.

### 3D characters

A folder with a `model.obj`, like `Alien/`:

- `model.obj` (required): a Wavefront OBJ model, the format Blender and most 3D model sites can export.
  Use Y up, facing forward (+Z), 1 unit = 1 block. Split the model into objects named `head`, `jaw`, `body`,
  `right_arm`, `left_arm`, `right_leg` and `left_leg`. Each part moves like the same part of a normal player
  (walking, looking around, swinging, sneaking), and the `jaw` opens while the player talks.
  Objects with other names move with the body.
- `texture.png` (optional): the texture. Without it, the model's vertex colors are used.
- `character.json` (optional): `name`, `height` (in blocks), `size` (scale), `turn` (degrees, if the model faces
  the wrong way), `jaw_angle` (degrees; negative opens the jaw upward like a lid, as the Burger's bun does),
  `butt` (where the fart cloud comes out), and per-part `pivot` (the joint) and `hand` (where held items go).
  Without pivots, they're guessed from each part's size. A part can have a pivot without being in the model:
  the Burger has only a head pivot, so its bun turns with the head.
- `icon.png` (optional): picture for the menu.

The four examples were made for this mod by [`tools/CharacterModels.java`](tools/CharacterModels.java), which
sculpts them from mathematical shapes, so they can be changed and rebuilt
(`java tools/CharacterModels.java <folder> [previewFolder] [name]`). The fart sounds are synthesized by
[`tools/make_fart_sounds.py`](tools/make_fart_sounds.py).

### Skin characters

- `Name.png`: a normal 64x64 Minecraft skin (old 64x32 skins work too). The file name is its name.
  Slim (3-pixel) arms are detected automatically.
- `Name_talking.png` (optional): the same skin with the mouth drawn open, shown while that player talks.
  Without it, the mod draws an open mouth on the face for you.
- `Name.json` (optional): `name`, `model` (`slim`/`wide`) and `mouth` (where the automatic mouth goes on the
  8x8 face, e.g. `{ "x": 1, "y": 5, "width": 6, "height": 2 }`).

## What gets hidden

For every character: armor, elytra, and heads or pumpkins worn on the head. For 3D characters also capes,
stuck arrows and bee stingers, which would float next to a differently shaped body. Name tags, the Simple Voice
Chat speaker icon, held items, parrots and riptide effects still show. In first person you keep seeing your
normal arm.

## How it works

- `AvatarRendererMixin` records on each player's render state which character to draw, how open its jaw is and
  how far it is bent over.
- `LivingEntityRendererMixin` draws the 3D model instead of the player model (copying the player model's pose
  each frame), leaves out the armor layers, and moves the held-item layer's hands onto the character's hands so
  Minecraft's own item code still handles bows, shields, spears and so on.
- `AbstractClientPlayerMixin` swaps the skin for skin characters.
- `VoiceChatPlugin` is a Simple Voice Chat client plugin. It receives the audio of nearby players, group chat
  and your own microphone. `TalkTracker` opens the mouth on the loud parts of speech (syllables) and closes it in
  the quieter gaps, relative to how loud that person usually is, and `JawAnimator` moves 3D jaws there smoothly.
- `FartDetector` copies the same audio to its own thread, turns it into 16 kHz and runs a Vosk recognizer per
  person, acting on its first guesses so the fart comes right after the word. `FartEffects` plays the animation:
  `PlayerModelMixin` and `FartPose` bend the upper body forward around the hips, which 3D characters, held items
  and the normal player model all follow.
- Without Simple Voice Chat installed, the mod still works; mouths just don't move, and only typed farts work.

Other mods that replace the player model (Figura, Entity Model Features and similar) may not work together with
this one.

## Building from source

Gradle must run on **Java 25** (required by Fabric Loom 1.19); the mod itself targets Java 21 like Minecraft 1.21.11.

```
./gradlew build
```

The jar ends up in `build/libs/`. The first build downloads the speech model from alphacephei.com and checks its
SHA-256 (task `prepareSpeechModel`).

`./gradlew runClientGameTest` starts the game, creates a world and checks the mod end to end (the example
characters, a skin character on another player in armor, talking jaws, farts typed in chat, both keys, the menus,
upgrading a folder from version 2), saving screenshots to `build/run/clientGameTest/screenshots/`. If Simple Voice
Chat is added to the test game, it also plays real voice chat audio and checks that mouths react to it, and with
recorded sentences (`-Dplayercharacters.test.speechClips=<folder>`) that saying "fart" works.

## License notes

The mod jar includes Vosk and its small English model, both under the Apache License 2.0. See
[`THIRD_PARTY_NOTICES.md`](THIRD_PARTY_NOTICES.md).
