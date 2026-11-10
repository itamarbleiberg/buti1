PLAYER CHARACTERS - how to add your own characters
====================================================

Every PNG file in this folder is one character. After adding or changing files,
open the character menu in game (K by default) and click "Reload".

1. The skin (required)
   Name.png
   A normal Minecraft skin, 64x64 pixels (old 64x32 skins work too).
   Make one in any skin editor or download one from a skin website.
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

The examples Robot and Frog can be deleted. They won't come back.
