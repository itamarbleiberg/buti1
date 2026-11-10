# Player Characters

A client-side Fabric mod for **Minecraft 1.21.11**. It shows players on a server as characters you choose: 3D
characters (the mod comes with an Alien, a Robot, a Frog, a Burger, a Penguin, SpongeBob, a Skinny Alien and Blue) or
skins you make yourself. The character's mouth opens while that player talks in
[Simple Voice Chat](https://modrinth.com/plugin/simple-voice-chat). When someone says "fart", their character bends
over and farts, and when they say "fuck you", it shows the middle finger and looks mad for a few seconds.

While a player is shown as a character, you don't see their armor, but you still see their name tag and the item
they're holding.

| Penguin | SpongeBob | Skinny Alien | Blue |
|---|---|---|---|
| ![Penguin](screenshots/finger-penguin.png) | ![SpongeBob](screenshots/finger-spongebob.png) | ![Skinny Alien](screenshots/finger-skinny-alien.png) | ![Blue](screenshots/finger-blue.png) |

These four are saying "fuck you".

| Robot talking | Frog talking | Burger talking (its top bun is the mouth) |
|---|---|---|
| ![Robot](screenshots/robot-talking.png) | ![Frog](screenshots/frog-talking.png) | ![Burger](screenshots/burger-talking.png) |

| Saying "fart" | | |
|---|---|---|
| ![Burger fart](screenshots/fart-burger.png) | ![Robot fart](screenshots/fart-robot.png) | ![Alien fart](screenshots/fart-alien.png) |

| Saying "fuck you" with a normal skin | Robot | Burger |
|---|---|---|
| ![Steve](screenshots/finger-steve.png) | ![Robot](screenshots/finger-robot.png) | ![Burger](screenshots/finger-burger.png) |

| Alien from the side, holding a sword | Normal player in armor | Same player as a skin character (armor hidden) |
|---|---|---|
| ![Alien side](screenshots/alien-side.png) | ![Armor](screenshots/armor-normal.png) | ![Armor hidden](screenshots/armor-hidden.png) |

| Character menu (K) | Choosing a character | Key binds |
|---|---|---|
| ![Menu](screenshots/menu.png) | ![Picker](screenshots/picker.png) | ![Key binds](screenshots/key-binds.png) |

## Install

1. Install [Fabric Loader](https://fabricmc.net/use/) 0.20.1 or newer for Minecraft 1.21.11.
2. Put these in your `.minecraft/mods` folder:
   - `player-characters-4.0.0.jar` (this mod), from the [Releases page](https://github.com/itamarbleiberg/buti1/releases)
   - [Fabric API](https://modrinth.com/mod/fabric-api) for 1.21.11
   - [Simple Voice Chat](https://modrinth.com/plugin/simple-voice-chat) for Fabric 1.21.11 (needed for talking mouths
     and the voice emotes; the server must have Simple Voice Chat too, as usual)

The server does **not** need this mod. Characters are only shown on your screen: other players keep seeing
normal players unless they install the mod and set it up themselves.

The jar is about 57 MB because it contains an offline speech recognizer (see [Voice emotes](#voice-emotes)).

Remove older versions of `player-characters-*.jar` from the mods folder first: two versions of the same mod don't load.

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
- **Farts** and **Middle finger** turn the voice emotes on or off.
- **Characters folder** opens the folder with your characters. **Reload** loads new or changed files.

Your choices are saved in `.minecraft/config/playercharacters.json`.

## Voice emotes

- **"fart"**: the character bends over, a green cloud puffs out of its butt and you hear a fart.
- **"fuck you"**: the character points its right arm at whoever is in front of it and shows the middle finger, puts
  away what it holds in that hand, and looks mad for about 3 seconds (angry eyebrows, and an angry cloud above its
  head). Players in a normal skin get a finger and eyebrows added to the Minecraft model.

This works for every character and for players shown in their own skin. Your own microphone counts too: you see your
character do it in third person, and other players who have the mod see it when they hear you. Typing the words in
chat does the same.

- The speech recognition is [Vosk](https://alphacephei.com/vosk/) with its small English model. It runs on your
  computer: no audio is sent anywhere, and it only hears the voices that Simple Voice Chat already plays for you.
- It listens for these two phrases. The recognizer only knows them and some words that sound like them ("far", "part",
  "farm", "thank you", "luck", "duck"...), so those aren't taken for them. In a test with 168 computer-voiced
  sentences in six English accents, it caught "fart" in 32 of 36 and "fuck you" in 36 of 36, and was fooled once
  (a false "fart") by the 96 sentences with lookalike words such as "thank you so much" or "what the fuck". Real
  voices and noisy microphones may do worse.
- The first time someone talks, the model is unpacked to `.minecraft/playercharacters/speech-model` (47 MB). It
  uses about 90 MB of memory while it's on, plus about 3 MB per person talking. Turning both emotes off frees it.
- Vosk works on Windows (64-bit), macOS and Linux (64-bit). Where it can't start, the menu says "Fart detection
  unavailable" and typing the words in chat still works.

## Characters

Characters live in `.minecraft/config/playercharacters/characters/`. The mod puts eight 3D examples there (Alien,
Robot, Frog, Burger, Penguin, SpongeBob, Skinny Alien and Blue) and a `README.txt` with the details below.

Upgrading: examples from older versions are updated (version 4 gives them angry faces and middle fingers), and the
version 2 skin Robot and Frog are replaced by the 3D ones, unless you changed those files. Changed files are kept.
If a skin and a 3D folder have the same name, the 3D one is used and the menu tells you which file was skipped.

### 3D characters

A folder with a `model.obj`, like `Alien/`:

- `model.obj` (required): a Wavefront OBJ model, the format Blender and most 3D model sites can export.
  Use Y up, facing forward (+Z), 1 unit = 1 block. Split the model into objects named `head`, `jaw`, `body`,
  `right_arm`, `left_arm`, `right_leg` and `left_leg`. Each part moves like the same part of a normal player
  (walking, looking around, swinging, sneaking), and the `jaw` opens while the player talks.
  Objects with other names move with the body.
  For the middle finger emote (all optional): objects named `mad_head` (or `mad_jaw`, `mad_body`...) are only drawn
  while the character is mad, `calm_head` and so on only while it isn't, and `middle_finger` is a right arm making
  the gesture, used instead of `right_arm`. Model it hanging down like `right_arm`, with the finger pointing forward:
  the emote raises the arm forward, which turns the finger up.
- `texture.png` (optional): the texture. Without it, the model's vertex colors are used.
- `character.json` (optional): `name`, `height` (in blocks), `size` (scale), `turn` (degrees, if the model faces
  the wrong way), `jaw_angle` (degrees; negative opens the jaw upward like a lid, as the Burger's bun does),
  `butt` (where the fart cloud comes out), `waddle` (degrees it rocks from side to side while walking),
  `head_turn` (0 to 1: how much of the player's looking around the head copies), and per-part `pivot` (the joint)
  and `hand` (where held items go).
  Without pivots, they're guessed from each part's size. A part can have a pivot without being in the model:
  the Burger has only a head pivot, so its bun turns with the head.
- `icon.png` (optional): picture for the menu.

The eight examples were made for this mod by [`tools/CharacterModels.java`](tools/CharacterModels.java), which
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
- `VoiceEmoteDetector` copies the same audio to its own thread, turns it into 16 kHz and runs a Vosk recognizer per
  person, acting on its first guesses so the emote comes right after the words. `FartEffects` and
  `MiddleFingerEmote` play the emotes: `PlayerModelMixin` and `CharacterPoses` bend the upper body, raise the arm and
  make characters waddle, which 3D characters, held items and the normal player model all follow.
  `VanillaEmoteParts` adds the finger and eyebrows to the normal player model.
- Without Simple Voice Chat installed, the mod still works; mouths just don't move, and only typed emotes work.

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
characters, a skin character on another player in armor, talking jaws, farts and middle fingers typed in chat, both
keys, the menus, upgrading folders from versions 2 and 3), saving screenshots to
`build/run/clientGameTest/screenshots/`. If Simple Voice Chat is added to the test game, it also plays real voice
chat audio and checks that mouths react to it, and with recorded sentences
(`-Dplayercharacters.test.speechClips=<folder>`) that saying "fart" and "fuck you" works and lookalikes don't.

## License notes

The mod jar includes Vosk and its small English model, both under the Apache License 2.0. See
[`THIRD_PARTY_NOTICES.md`](THIRD_PARTY_NOTICES.md).
