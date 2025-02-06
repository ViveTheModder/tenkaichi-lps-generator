# tenkaichi-lps-generator
A modding tool (supporting both CLI and GUI) for DBZ Budokai Tenkaichi 2 &amp; 3 (both PS2 and Wii versions) that automatically generates and assigns LPS files (for lip-syncing) to PAK files containing them (mainly character costumes).

Recommended to be used on WAV files with a sample rate/frequency of 24000 Hz, obtained from converting ADX files to WAV with [PESSFC](https://www.moddingway.com/file/1640.html).

The tool will work as intended on PAK files that clearly belong to the character's costume (has to end with ``Xp.pak`` or ``Xp_dmg.pak``, where ``X`` is the costume number from 1 to 4).

It works on other PAKs as well, like the ones used in Dragon History (e.g. ``LPS-XX-B-YY.pak``) and menu files (not recommended unless the WAVs are renamed accordingly, because every even LPS is for Japanese voices, while the odd LPS files correspond to the English voices).

Also, the character's name in the WAV files has to match the character's name in the PAK files in order for any changes to be made. 

Example: ``Goku_0_500_US.wav`` & ``Goku_0_1p.pak``.

Because the program gets the audio file ID from the WAV's file name, it has to be named either ``X_YYY_ZZ.wav`` or ``X-YYY-ZZ.wav``.
* X ---> Name (of a character, menu, scenario etc.);
* YYY -> 3-digit number (this is the audio file ID);
* ZZ --> Region (either US or JP; this only really matters for the character costume files).

# Introduction
## Origin
Back when I was "hired" by Team BT4 to help with localization (aka the International Version), I had to do all the lip-syncing manually.

The greatest example of this is Dragon History. All the lip-syncing there (besides the stuff for the Goku vs. Uub fight) was done by me.

I worked on some characters' lip-syncing too (mainly the Goku variants), but that quickly stopped once [pxgamer13](https://github.com/pxgamer13) made his take on an automatic lip-syncing tool.

For those who have tried out the tool (either former team members or friends of current ones), there is no doubt about the end results, but rather, what it took to get there.

Originally, the tool would dump out a lot of duplicate keyframes, essentially making the character's mouth open and close in the same frame, which is pointless and takes up space.

I decompiled the tool's source code, given how it is for stuff written in Java, and I was beyond disappointed with the code.

Nothing wrong with borrowing code from StackOverflow, but please don't make it so blatantly obvious. English and Spanish names throughout the same method gave it away.

Also, there is literally zero point in using ``java.time.LocalTime`` and ``java.time.temporal.ChronoUnit`` - the LPS file format does not need to be that precise.

## Breakdown
Both pxgamer and I started off with a nearly identical approach:
* Scan WAV file and get the essentials (audio, format, frame size, frame length, frame rate);
* Based on the frame size (2 bytes for 24000 Hz WAV files), scan every frame from the WAV's data;
* Calculate the RMS (Root Mean Square) from the retrieved samples (better explanation of it [here](https://stackoverflow.com/questions/4953045/im-trying-to-get-the-volume-level-of-a-wav-file-using-javas-sound-api-but-hav)).

Everything else from this point was purely my idea:
* Convert each RMS to dB (decibels), even though [RMS isn't the best indicator of loudness](https://forum.audacityteam.org/t/getting-precise-db-level-from-playback-meter-reading/60457/4);
* Compare each dB with a predetermined threshold: 45 dB, which is right in the middle of a whisper (30 dB) and normal conversation (60 dB).
* If it exceeds the threshold, then the character's mouth is open. Otherwise, it is closed.
* Filter out the results by skipping duplicate frames (after being estimated and then converted to "keyframes", given Tenkaichi's 60 fps framerate);
* If the last keyframe indicates that the mouth is open, then another keyframe is added (that is equal to the total number of frames) to ensure that the mouth is closed.
* Filter out the results once more by calculating the difference/interval of every 2 keyframes and effectively skipping keyframes that only have an interval of 1 frame.

# Demonstration
## CLI
This version does not let the user change the threshold. It is set to 45 dB by default.
![bt-lps-gen-1](https://github.com/user-attachments/assets/9c8a839b-19c6-4aac-a2d7-f95442ee2964)

![bt-lps-gen-2](https://github.com/user-attachments/assets/e8826733-3d69-4c58-8627-4aa7bcda6fdb)

## GUI
Not shown in the screenshot, but I changed the threshold to 25 dB. It would have been 45 dB otherwise.
![image](https://github.com/user-attachments/assets/b26129d6-a80d-4651-baab-00fd88800598)

![image](https://github.com/user-attachments/assets/2d398cd3-4700-4a36-a8ff-d52f976ff214)

![image](https://github.com/user-attachments/assets/f2d55b9c-3585-4634-87cd-b3805f248b78)

![image](https://github.com/user-attachments/assets/20839b7d-c615-4c28-924c-3d45491ce3ec)

![image](https://github.com/user-attachments/assets/48ca20d9-3998-427f-8c4e-b5ed6aed1f50)

![image](https://github.com/user-attachments/assets/7cdd47ee-ef36-4926-a651-74b370fee7e1)

![image](https://github.com/user-attachments/assets/c54e941a-6d42-488d-b1fb-5607abe97ad4)

![image](https://github.com/user-attachments/assets/723e7613-3377-437b-8dc1-7ece35a0f615)

# Results
Here is a file comparison of two character costume files that will be used for an upcoming mod...
![bt-lps-gen-3](https://github.com/user-attachments/assets/61f7e941-3caa-4d00-a0b6-324e502c71ed)

Here is the very first test I did of this tool, with an iconic voice line from Budokai 2: "Hahaa, I'm so EXCITED!"

https://github.com/user-attachments/assets/afcb2407-b6d3-4316-849a-fb0aa22ef494

[jagger1407](https://github.com/jagger1407) looking at my tool:

https://github.com/user-attachments/assets/cb66e645-d8f6-4349-84e1-f1ffc9cbb47f

