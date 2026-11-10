PLAYER CHARACTERS - how to add your own characters
====================================================

After adding or changing files here, open the character menu in game (K by default)
and click "Reload". Players shown as a character don't show their armor, but you
still see their name tag and what they hold.

There are two kinds of characters.


3D CHARACTERS (like the Alien, Robot, Frog, Burger, Penguin, SpongeBob, Skinny Alien
and 67, which is in the folder "Blue")
-------------------------------------------------------------------------------------
A folder with these files. The folder name is the character's name.

  Alien/model.obj        the 3D model (required)
  Alien/texture.png      its texture (optional, otherwise the model's vertex colors are used)
  Alien/character.json   settings (optional)
  Alien/icon.png         picture for the menu (optional)

Make or download a model, then export it from Blender as Wavefront OBJ (.obj), with
Y up, facing forward (+Z), 1 unit = 1 block. Split it into separate objects named:

  head, jaw, body, right_arm, left_arm, right_leg, left_leg

Each part moves like the same part of a normal player. The "jaw" opens while the player
talks in Simple Voice Chat. Objects with other names move with the body.

For the middle finger emote, a model can also have (all optional):

  mad_head        shown only while the character is mad, for example angry eyebrows.
                  Any part works: mad_jaw, mad_body...
  calm_head       shown only while it is NOT mad (things the angry face replaces).
  middle_finger   a right arm with the middle finger up, used instead of right_arm
                  during the emote. Model it hanging down like right_arm, with the
                  finger pointing forward: the arm is raised to point forward, which
                  turns the finger up.

character.json, for example:

  {
    "name": "Alien",
    "height": 1.9,
    "turn": 0,
    "jaw_angle": 32,
    "butt": [0, 0.95, -0.09],
    "parts": {
      "jaw": { "pivot": [0, 1.705, 0] },
      "right_arm": { "pivot": [-0.185, 1.415, -0.01], "hand": [-0.232, 0.715, 0.088] }
    }
  }

  name       name shown in the menu (default: the folder name)
  height     make the model this many blocks tall (default: as it is, or 1.9 if it is
             much too big or small, for example when it was made in centimeters)
  size       or scale it by this number instead
  turn       degrees to turn the model if it faces the wrong way, e.g. 180
  jaw_angle  how far the jaw opens, in degrees (default 30). Use a negative number to
             open it upward like a lid: the Burger's top bun uses -38.
  butt       where the fart cloud comes out (default: the middle of the body's back,
             just above the legs)
  waddle     degrees the character rocks from side to side while walking (the
             Penguin uses 8)
  head_turn  how much of the player's looking around the head copies, from 0 (the
             head stays still) to 1 (all of it, the default). SpongeBob uses 0.3,
             because his whole sponge is his head.
  smoke      where a thin line of smoke rises from, moving with the head, like the
             tip of the Penguin's cigarette (optional)
  parts      "pivot" is the joint a part turns around, "hand" is where held items go.
             Both are in the model's own coordinates. Without them, they are guessed
             from the size of each part. A part can have a pivot without being in the
             model: the Burger has no "head" object, only a head pivot, so its top bun
             (its "jaw") turns with the head.


SKIN CHARACTERS
---------------
Every PNG file in this folder is one character.

1. The skin (required)
   Name.png
   A normal Minecraft skin, 64x64 pixels (old 64x32 skins work too).
   The file name is the character's name, e.g. "Pirate Captain.png".

2. The talking face (optional)
   Name_talking.png
   The same skin with the mouth drawn open. It is shown while the player talks
   in Simple Voice Chat.
   If you don't make one, the mod draws an open mouth on the face for you.

3. Settings (optional)
   Name.json, for example:

   {
     "name": "Frog",
     "model": "slim",
     "mouth": { "x": 1, "y": 5, "width": 6, "height": 2 }
   }

   name   - name shown in the menu (default: the file name)
   model  - "slim" for 3-pixel-wide arms, "wide" for 4-pixel-wide arms
            (default: detected from the skin)
   mouth  - where the automatic open mouth is drawn on the 8x8 face, counted in
            pixels from the top-left corner of the face
            (default: x 3, y 6, width 2, height 2, which fits most skins)

A skin and a 3D folder can't have the same name. If they do, the 3D one is used.


EMOTES
------
When a player says "fart" in voice chat (or types it in chat), their character bends
over and farts. When they say "fuck you" or "fuck off", their character shows the middle
finger and looks mad for a few seconds. This works for every character, and for players
in their own skin. Turn them off with the "Farts" and "Middle finger" buttons in the
character menu.

In first person you see your 3D character's own arm. When you show the middle finger,
your fist comes up in front of you, and when you fart, your screen turns green and
bubbles pop.

The example characters can be deleted. They won't come back.
