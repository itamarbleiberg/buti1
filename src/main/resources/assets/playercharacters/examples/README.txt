PLAYER CHARACTERS - how to add your own characters
====================================================

After adding or changing files here, open the character menu in game (K by default)
and click "Reload". Players shown as a character don't show their armor, but you
still see their name tag and what they hold.

There are two kinds of characters.


3D CHARACTERS (like the Alien)
------------------------------
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

character.json, for example:

  {
    "name": "Alien",
    "height": 1.9,
    "turn": 0,
    "jaw_angle": 32,
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
  jaw_angle  how far the jaw opens, in degrees (default 30)
  parts      "pivot" is the joint a part turns around, "hand" is where held items go.
             Both are in the model's own coordinates. Without them, they are guessed
             from the size of each part.


SKIN CHARACTERS (like Robot and Frog)
-------------------------------------
Every PNG file in this folder is one character.

1. The skin (required)
   Name.png
   A normal Minecraft skin, 64x64 pixels (old 64x32 skins work too).
   The file name is the character's name, e.g. "Pirate Captain.png".

2. The talking face (optional)
   Name_talking.png
   The same skin with the mouth drawn open. It is shown while the player talks
   in Simple Voice Chat. See Robot_talking.png for an example.
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

The example characters can be deleted. They won't come back.
