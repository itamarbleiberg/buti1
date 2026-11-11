PLAYER CHARACTERS - how to add your own characters
====================================================

After adding or changing files here, open the character menu in game (K by default)
and click "Reload". Players shown as a character don't show their armor, but you
still see their name tag and what they hold (unless the character has
"hide_held_items", like the White Queen and the Black Wolf).

There are two kinds of characters.


3D CHARACTERS (like the Alien, Robot, Frog, Burger, Penguin, SpongeBob, Skinny Alien,
White Queen, Black Wolf and 67, which is in the folder "Blue")
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
                  turns the finger up. You don't need it if the right hand has
                  fingers (see below): then the hand's own fingers make the gesture.
  blink_head      closed eyelids, shown for a moment every few seconds so the
                  character blinks. Any part works: blink_jaw, blink_body...

More optional objects:

  glow_head       glows in the dark, like the Black Wolf's eyes and runes.
                  Any part works: glow_body, glow_right_arm...
  tail_left       objects whose name starts with "tail" sway by themselves,
                  bending along their length: slowly while standing, faster
                  while walking, and they wag during emotes. The wolf has
                  tail_left, tail_middle and tail_right.
  glow_tail_left  glows, and sways with the tail of the same name.
  fp_right_arm    a more detailed right arm, only drawn in first person (also
                  fp_left_arm and fp_middle_finger). Without them, the normal
                  arms are used.
  right_index     fingers that curl: right_thumb, right_index, right_middle,
                  right_ring and right_little (or right_pinky), and the same with
                  left_. Each finger is its own object and moves with the hand. A
                  hand can have fewer, like a cartoon glove without a ring finger.
                  Emotes curl them into fists, pointing, rock horns and so on.
                  fp_right_index and the others are their first-person versions.
  right_hand      something held in the hand that moves with it as a whole, like
                  the White Queen's rifle (also left_hand). glow_right_hand glows,
                  and fp_right_hand is its first-person version.

Arms bend at the elbow in emotes. The arm's surface bends over a short stretch around
the elbow, so it doesn't tear, and the forearm twists toward the wrist.

Far away, the mod draws a simpler copy of each model with fewer polygons, so many
detailed characters don't slow the game down.

character.json, for example:

  {
    "name": "Alien",
    "height": 1.9,
    "turn": 0,
    "jaw_angle": 32,
    "butt": [0, 0.95, -0.09],
    "parts": {
      "jaw": { "pivot": [0, 1.683, 0.171] },
      "right_arm": { "pivot": [-0.185, 1.404, 0.093], "hand": [-0.232, 0.704, 0.191],
                     "elbow": [-0.215, 1.074, 0.103], "wrist": [-0.226, 0.774, 0.148] },
      "right_index": { "pivot": [-0.228, 0.701, 0.176], "tip": [-0.232, 0.555, 0.205],
                       "curl": [1, 0, 0] }
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
  smoke      where smoke rises from, moving with the head, like the tip of the
             Penguin's cigarette: a thin gray plume, and a puff every few seconds
             (optional)
  bounce     how far (in blocks) the character bobs up and down with each step
             while walking, e.g. 0.05 (default 0: no bobbing)
  parts      "pivot" is the joint a part turns around, "hand" is where held items go.
             Both are in the model's own coordinates. Without them, they are guessed
             from the size of each part. A part can have a pivot without being in the
             model: the Burger has no "head" object, only a head pivot, so its top bun
             (its "jaw") turns with the head.
             Arms can also have "elbow" (the elbow joint) and "wrist" (where the
             forearm ends and its twist stops). Without them, the elbow is guessed
             about halfway down the arm. The forearm bends toward the front.
             Fingers have "pivot" (the knuckle), "tip" and "curl" (the direction
             the tip moves when the finger curls, toward the palm). Optional:
             "joints" (where it bends, as fractions of its length from the
             knuckle; default [0, 0.44, 0.74], or [0, 0.5] for a thumb), "angles"
             (degrees each joint bends in a fist; default [75, 95, 65], or
             [40, 55] for a thumb) and "rigid" (true bends sharply at the joints,
             like the Robot's grippers, instead of over a short stretch).
  stance     "four_legs" for animals, like the Black Wolf. Then right_arm and
             left_arm are the front legs and right_leg and left_leg the back legs
             (front_right_leg, back_left_leg... work as names too). They trot in
             diagonal pairs, and for emotes the character stands up on its back legs.
             (default "two_legs")
  arms       "rifle" holds both arms out where the player looks, like the White
             Queen aiming her rifle. Model the rifle as part of right_arm, or
             as right_hand.
             (default "normal")
  hide_held_items
             true doesn't show the items the player holds (the queen's hands are
             on her rifle, the wolf has paws)
  particles  particles that keep coming out of the character, for example the
             flames on the wolf's tails:

               "particles": [
                 { "particle": "soul_fire_flame", "part": "tail_left", "every": 4,
                   "spread": 0.035, "velocity": [0, 0.012, 0] },
                 { "particle": "dust", "color": "#5FE6FF", "size": 0.5, "part": "head",
                   "at": [0.097, 1.432, 1.02], "every": 13 }
               ]

             particle  a Minecraft particle name: flame, soul_fire_flame, smoke,
                       heart, end_rod, squid_ink, dust...
             part      the part it moves with (default "body"). For a tail, it comes
                       out of the tip unless "at" is given.
             at        where it comes out, in the model's own coordinates
             every     ticks between two particles (default 4; 20 ticks = 1 second)
             when      "always" (the default), "moving", "sprinting" or "still"
             spread    random distance from that point, in blocks
             velocity  blocks per tick: toward the character's left, up, forward
             color     for "dust" only, like "#5FE6FF" (and "size", default 1)


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
The emote wheel (B by default) has 32 emotes, from the Default Dance and the 67 to the
Floss, the Griddy, the Robot and push-ups. Point the mouse at one or scroll to it, then
click. You can walk, run and jump while doing an emote; sneaking stops it. Arms bend at
the elbows in emotes, also for normal skins (a player wearing a chestplate keeps straight
arms, because the armor can't bend), and the 3D characters' fingers make fists, point
and show rock horns. Dances play a
little note block tune, or your own music: put .ogg files named after the emotes in the
"music" folder next to this one (the "Music folder" button in the character menu).

The fart and the middle finger are on the wheel too. Typing "fart" in chat also makes your
character fart, and "fuck you" or "fuck off" makes it show the middle finger and look mad
for a few seconds. This works for every character, and for players in their own skin. Turn
them off with the "Farts" and "Middle finger" buttons in the character menu.

In first person you see your 3D character's own arm, with relaxed fingers that move a
little and close into a fist when you punch. When you show the middle finger, your hand
comes up in front of you, palm toward you, and when you fart, your screen turns green
and bubbles pop.

Players who have this mod see your character and your emotes: they are told through /me
chat messages, like "* Steve is playing as Black Wolf" and "* Steve does the Default
Dance" (turn that off with "Share with players" in the character menu). They see your
character only if they have one with the same name. The ten examples come with the mod,
so those work unless they deleted them.

The example characters can be deleted. They won't come back.
