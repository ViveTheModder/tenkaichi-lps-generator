package cmd;

//Tenkaichi LPS Generator v1.8 by ViveTheJoestar
import java.awt.Desktop;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Scanner;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;

public class Main {
	public static boolean hasNoValidWAVs = false, disableFilter = false, wiiMode = false; //Wii Mode is also used for Raging Blast
	static boolean startsClosed = false; //condition of 1st keyframe (closed/true or open/false)
	public static int numPakContents, pakTotal = 0, progBarMax = 0, threshold = 45, validPaks = 0, validWavs = 0, wavTotal = 0;
	private static final String TITLE = "Tenkaichi LPS Generator v1.7";

	private static boolean isCharaCostumePak(File pakRef) throws IOException {
		RandomAccessFile pak = new RandomAccessFile(pakRef, "r");
		numPakContents = LittleEndian.getInt(pak.readInt());
		pak.seek((numPakContents + 1) * 4);
		int fileSize = LittleEndian.getInt(pak.readInt());
		int actualFileSize = (int) pak.length();
		pak.close();
		if (fileSize == actualFileSize && numPakContents >= 250 && numPakContents <= 252) return true;
		return false;
	}
	private static byte[] getLpsFileContents(short[] keyframes, int numKeyframes) {
		short fileSize = (short) (16 + (numKeyframes * 4));
		short mouthCondBoolean = 1;
		if (startsClosed == true) mouthCondBoolean = 0;
		//make lps file size the nearest multiple of 16
		if (fileSize % 64 != 0) fileSize = (short) (fileSize + 64 - (fileSize % 64));
		byte[] lps = new byte[fileSize];
		byte[] LIPS = {0x4C, 0x49, 0x50, 0x53};
		if (wiiMode)
			LIPS[3] = 0x57; //change S to W if Wii/RB mode is enabled
		System.arraycopy(LIPS, 0, lps, 0, 4);
		System.arraycopy(LittleEndian.getByteArrayFromInt(2), 0, lps, 4, 4);
		System.arraycopy(LittleEndian.getByteArrayFromInt(numKeyframes), 0, lps, 8, 4);
		int pos = 16;
		for (int i = 0; i < numKeyframes; i++) {
			System.arraycopy(LittleEndian.getByteArrayFromShort(keyframes[i]), 0, lps, pos, 2);
			pos += 2;
			System.arraycopy(LittleEndian.getByteArrayFromShort((short) (mouthCondBoolean % 2)), 0, lps, pos, 2);
			pos += 2;
			mouthCondBoolean++;
		}
		return lps;
	}
	private static short[] getLpsKeyframes(File wav) throws UnsupportedAudioFileException, IOException {
		ArrayList<Short> list = new ArrayList<>();
		AudioInputStream stream = AudioSystem.getAudioInputStream(wav);
		AudioFormat format = stream.getFormat();
		String mouthCond = ""; //initial mouth condition (open or closed)
		String tempCond = ""; //temporary mouth condition, used to skip duplicate conditions
		short prevFrame = -1; //previous frame, used to compare two consecutive frames
		long numFrameBytes = stream.getFrameLength();
		int numBytesPerFrame = format.getFrameSize();
		float duration = numFrameBytes / format.getFrameRate();
		short audioFrames = (short) Math.round(duration * 60);
		byte[] audioBytes = new byte[numBytesPerFrame];
		for (int i = 0; i < numFrameBytes; i++) {
			stream.read(audioBytes);
			long sampleSum = 0;
			for (int j = 0; j < numBytesPerFrame; j += 2) {
				short sample = (short) ((audioBytes[j] & 0xFF) | (audioBytes[j + 1] << 8));
				sampleSum += sample * sample; //sum of squares
			}
			double rms = Math.sqrt((double) sampleSum / numBytesPerFrame); //root mean square
			if (i == 0 && rms == 0) startsClosed = true; //0 rms means the volume is -Infinity dB
			double db = 20 * Math.log10(rms); //decibels
			double time = i / 24000.0; //current fraction of a second
			short currFrame = (short) Math.round(time * 60);
			//test open & closed ranges by filtering the results (this could also be done
			//with a boolean variable lol)
			if (db < threshold) mouthCond = "Closed";
			else mouthCond = "Open";
			if (prevFrame != currFrame) {
				if (!mouthCond.equals(tempCond))
					list.add(currFrame);
				tempCond = mouthCond;
			}
			prevFrame = currFrame;
		}
		//as a security measure, add one last keyframe that is always closed
		if (tempCond.equals("Open")) list.add(audioFrames);
		//manual way of doing list.toArray(), which traverses the list twice
		int numKeyframes = list.size();
		short[] keyframes = new short[numKeyframes];
		for (int i = 0; i < numKeyframes; i++) keyframes[i] = list.get(i);
		return keyframes;
	}
	public static void assignLpsToPak(File[] pakFiles, File[] wavFiles) throws UnsupportedAudioFileException, IOException {
		int cnt = 0;
		pakTotal = pakFiles.length;
		wavTotal = wavFiles.length;
		progBarMax = pakTotal * wavTotal;
		if (gui.App.bar != null)
			gui.App.bar.setMaximum(progBarMax);
		for (File wav : wavFiles) {
			String wavName = wav.getName().toLowerCase().replace(".wav", "");
			String[] wavNameArray = wavName.split("_");
			short[] keyframes = getLpsKeyframes(wav);
			short[] openMouthIntervals = new short[(keyframes.length - 1) / 2];
			int intervalIndex = 0, displacement = 0;

			for (int i = 0; i < keyframes.length; i += 2) {
				if (startsClosed == true)
					displacement = 1;
				if (intervalIndex < openMouthIntervals.length)
					openMouthIntervals[intervalIndex] = (short) (keyframes[i + 1 + displacement]
							- keyframes[i + displacement]);
				intervalIndex++;
			}
			int numKeyframes = keyframes.length;
			//filter keyframes based on open mouth intervals
			if (!disableFilter) {
				for (int i = 0; i < openMouthIntervals.length; i++) {
					int remainingKeyframesSize = numKeyframes - (i + displacement + 3);
					if (openMouthIntervals[i] == 1 && remainingKeyframesSize > 1) //exclude keyframes with a
																					//difference/interval of 1 frame
					{
						short[] remainingKeyframes = new short[remainingKeyframesSize];
						if (i + 4 + remainingKeyframesSize <= numKeyframes) //prevent System.arraycopy() from
																			//softlocking the program
						{
							System.arraycopy(keyframes, i + 4, remainingKeyframes, 0, remainingKeyframes.length);
							numKeyframes -= 2;
							System.arraycopy(remainingKeyframes, 0, keyframes, i + displacement + 1,
									remainingKeyframes.length);
						}
					}
				}
			}
			byte[] lpsContents = getLpsFileContents(keyframes, numKeyframes);
			for (File pak : pakFiles) {
				int wavID = -1;
				String pakName = pak.getName().toLowerCase();
				//check delimiter (underscore or hyphen/dash/minus)
				if (wavName.contains("_"))
					wavNameArray = wavName.split("_");
				else if (wavName.contains("-"))
					wavNameArray = wavName.split("-");
				else {
					wavTotal--;
					if (wavTotal == 0)
						hasNoValidWAVs = true;
					continue;
				}
				//check if name ends with US/JP (region) to then get the WAV ID (zero-indexed)
				if (wavName.endsWith("us") || wavName.endsWith("jp"))
					wavID = Integer.parseInt(wavNameArray[wavNameArray.length - 2]);
				else if (wavName.matches("[A-Za-z0-9]+"))
					wavID = Integer.parseInt(wavNameArray[wavNameArray.length - 1]);
				else
					continue; //skip WAV files containing special characters or no WAV IDs (numbers) at all
				//check if current PAK file is a costume file, then change the WAV ID based on
				//the WAV's language
				if (isCharaCostumePak(pak)) {
					if (validPaks < pakTotal) validPaks++;
					String charaNameFromPak = null;
					if (pakName.endsWith("p.pak")) charaNameFromPak = pakName.substring(0, pakName.length() - 7);
					else if (pakName.endsWith("p_dmg.pak")) charaNameFromPak = pakName.substring(0, pakName.length() - 11);
					else continue; //prevent assist objects (costumes without parameters) from being detected
					String charaNameFromWav = wavName.substring(0, wavName.length() - 7).toLowerCase();
					int newWavID = wavID;
					if (wavNameArray[wavNameArray.length - 1].endsWith("us")) newWavID = 152 + (wavID % 500);
					else if (wavNameArray[wavNameArray.length - 1].endsWith("jp")) newWavID = 52 + (wavID % 500);
					if (numPakContents == 250)
						newWavID -= 4; //this only applies to Budokai Tenkaichi 2's character costumes
					//only overwrite current character costume PAK if the character name matches with the one from the WAV
					if (charaNameFromPak.equals(charaNameFromWav)) {
						validWavs++;
						gui.App.bar.setMaximum(progBarMax -= 2);
						System.out.println("Assigning generated LPS (from " + wavName.toUpperCase() + ".WAV) to "
						+ pakName.toUpperCase() + "...");
						overwritePakFile(pak, lpsContents, newWavID);
						cnt += 2;
					}
				} else if (pakName.contains("lps")) {
					//all blame & praise goes to MetalFrieza3000
					if (pakName.startsWith("bt4")) {
						if (validPaks < pakTotal)
							validPaks++;
						String charaNameFromPak = pakName.substring(0, pakName.length() - 11).replace("bt4_", "");
						String charaNameFromWav = wavName.substring(0, wavName.length() - 7).toLowerCase();
						String[] pakNameArray = pakName.split("_");
						String langFromPak = pakNameArray[pakNameArray.length - 1].replace(".pak", "");
						String langFromWav = wavNameArray[wavNameArray.length - 1];

						int newWavID = wavID % 500;
						if (charaNameFromPak.equals(charaNameFromWav) && langFromPak.equals(langFromWav)) {
							validWavs++;
							gui.App.bar.setMaximum(progBarMax -= 2);
							System.out.println("Assigning generated LPS (from " + wavName.toUpperCase() + ".WAV) to "
									+ pakName.toUpperCase() + "...");
							overwritePakFile(pak, lpsContents, newWavID);
							cnt += 2;
						}
					} 
					//check if WAV is from story mode (VIC -> Voice In //Cutscene?)
					else if (wavNameArray[0].equals("vic")) {
						if (validPaks < pakTotal)
							validPaks++;
						String[] pakNameArray = pakName.split("-");
						int pakID = Integer.parseInt(pakNameArray[pakNameArray.length - 1].replace(".pak", ""));
						int gscID = Integer.parseInt(wavNameArray[1]);
						String langFromPak = pakNameArray[1];
						if (langFromPak.equals(wavNameArray[wavNameArray.length - 1]) && pakID == gscID) {
							validWavs++;
							gui.App.bar.setMaximum(progBarMax -= 2);
							System.out.println("Assigning generated LPS (from " + wavName.toUpperCase() + ".WAV) to "
									+ pakName.toUpperCase() + "...");
							overwritePakFile(pak, lpsContents, wavID);
							cnt += 2;
						}
					}
				} else if (pakName.endsWith("lips.pak")) {
					if (validPaks<pakTotal)
						validPaks++;
					String langFromWav = wavNameArray[wavNameArray.length - 1];
					int newWavID = wavID;
					if (langFromWav.endsWith("us"))
						newWavID = 2 * wavID + 1;
					else if (langFromWav.endsWith("jp"))
						newWavID = 2 * wavID;

					validWavs++;
					gui.App.bar.setMaximum(progBarMax -= 2);
					System.out.println("Assigning generated LPS (from " + wavName.toUpperCase() + ".WAV) to "
							+ pakName.toUpperCase() + "...");
					overwritePakFile(pak, lpsContents, newWavID);
					cnt += 2;
				}
				//increment progress bar percentage
				if (gui.App.bar != null) gui.App.bar.setValue(cnt);
			}
		}
	}
	private static void overwritePakFile(File pakRef, byte[] lpsContents, int id) throws IOException {
		RandomAccessFile pak = new RandomAccessFile(pakRef, "rw");
		int numSizes = LittleEndian.getInt(pak.readInt());
		int[] positions = new int[numSizes + 1];
		int[] sizes = new int[numSizes];
		int tempPos = 0;

		for (int i=1; i<=numSizes; i++) //initialize positions and sizes
		{
			if (tempPos==0) positions[i-1] = LittleEndian.getInt(pak.readInt()); //current position
			else positions[i-1] = tempPos;
			positions[i] = LittleEndian.getInt(pak.readInt()); //next position
			tempPos = positions[i]; //temporary position which exists to prevent reading the same positions twice
			sizes[i-1] = positions[i] - positions[i-1];
		}

		int difference = sizes[id] - lpsContents.length;
		if (difference != 0) {
			//fix index
			pak.seek((id + 2) * 4);
			for (int i = id + 1; i <= numSizes; i++)
				pak.writeInt(LittleEndian.getInt(positions[i] - difference));
			pak.seek(positions[id + 1]);
			int fullFileSize = (int) pak.length();
			int restOfFileSize = fullFileSize - positions[id + 1];
			byte[] restOfFile = new byte[restOfFileSize];
			pak.read(restOfFile); //copy the rest of the file contents before overwriting
			pak.seek(positions[id]);
			//actual overwriting process
			pak.write(lpsContents);
			pak.write(restOfFile);
			pak.setLength(fullFileSize - difference);
			pak.close();
		} else {
			pak.seek(positions[id]);
			pak.write(lpsContents);
		}
	}
	public static void setErrorLog(Exception e) {
		File errorLog = new File("errors.log");
		try {
			FileWriter logWriter = new FileWriter(errorLog, true);
			logWriter.append(new SimpleDateFormat("dd-MM-yy-hh-mm-ss").format(new Date()) + ":\n"
			+ e.getClass().getSimpleName() + ": " + e.getMessage() + "\n");
			logWriter.close();
			Desktop.getDesktop().open(errorLog);
		} catch (IOException e1) {
			e1.printStackTrace();
		}
		System.exit(1);
	}
	public static void swapLpsByteOrder(File[] lpsDirs) throws IOException {
		byte[] intInput = new byte[4], shortInput = new byte[2];
		int lpsType = 'S';
		String byteOrder = "";
		RandomAccessFile[] lpsFiles = new RandomAccessFile[lpsDirs.length];
		for (int i = 0; i < lpsDirs.length; i++) {
			lpsFiles[i] = new RandomAccessFile(lpsDirs[i], "rw");
			for (int pos = 0; pos < 16; pos += 4) {
				lpsFiles[i].seek(pos);
				lpsFiles[i].read(intInput);
				if (pos == 0) {
					//specify output LPS type & order based on input LPS
					if (new String(intInput).equals("LIPW")) {
						lpsType = 'S';
						byteOrder = "Little Endian (PS2 BT2/BT3)";
					} else {
						lpsType = 'W';
						byteOrder = "Big Endian (Wii BT2/BT3, PS3 RB1/RB2)";
					}
				} else {
					lpsFiles[i].seek(pos);
					LittleEndian.swapByteArrayOrder(intInput, true);
					lpsFiles[i].write(intInput);
				}
			}
			for (int pos = 16; pos < lpsFiles[i].length() - 16; pos += 2) {
				lpsFiles[i].seek(pos);
				lpsFiles[i].read(shortInput);
				lpsFiles[i].seek(pos);
				LittleEndian.swapByteArrayOrder(shortInput, false);
				lpsFiles[i].write(shortInput);
			}
			lpsFiles[i].seek(3);
			lpsFiles[i].write(lpsType);
			lpsFiles[i].close();
			System.out.println("Converted " + lpsDirs[i].getName() + " to " + byteOrder + "!");
		}
	}
	public static void main(String[] args) {
		String helpMsg = TITLE
		+ "\nAutomatically generate Lip-Syncing files and assign them to PAK files (for characters or menus).\n"
		+ "Here is a list of the only arguments that can be used. Use -h or -help to print this out again.\n\n"
		+ "* -w (or -wii)\nEnable support for PAK files that use the Big Endian byte order (from Wii BT2/BT3 or PS3 RB1/RB2).\n"
		+ "* -p (or -ps2)\nEnable support for PAK files that use the Little Endian byte order (from PS2 BT2/BT3).\n"
		+ "* -d (must be passed as 2nd arg)\nDisable keyframe filtering (to prevent rare softlocks).\n"
		+ "* -s (or -swap)\nSwap the byte order of the LPS files in the specified folder.\n";
		long start = System.currentTimeMillis();
		if (args.length > 0) {
			for (String arg : args) {
				if (arg.equals("-w") || arg.equals("-wii")) wiiMode = true;
				else if (arg.equals("-p") || arg.equals("-ps2")) wiiMode = false;
				else if (arg.equals("-d")) {
					disableFilter = true;
					if (args[0].equals(arg)) {
						gui.App.main(null);
						return;
					}
				} else if (arg.equals("-s") || arg.equals("-swap")) {
					try {
						Scanner sc = new Scanner(System.in);
						String input;
						File[] lpsFiles = null;
						while (lpsFiles == null) {
							System.out.println("Enter a valid directory containing LPS files:");
							input = sc.nextLine();
							File lpsFolder = new File(input);
							if (lpsFolder.isDirectory()) {
								lpsFiles = lpsFolder.listFiles((dir, name) -> (name.toLowerCase().endsWith("lps")));
								if (lpsFiles.length == 0) {
									lpsFiles = null;
									System.out.println("Directory does NOT contain LPS files. Try again!\n");
								}
							}
						}
						sc.close();
						start = System.currentTimeMillis();
						swapLpsByteOrder(lpsFiles);
						long end = System.currentTimeMillis();
						System.out.println("\nTime Elapsed: " + (end - start) / 1000.0 + " s");
						return;
					} catch (IOException e) {
						setErrorLog(e);
					}
				} else if (arg.equals("-h") || arg.equals("-help")) {
					System.out.println(helpMsg);
					System.exit(0);
				} else {
					System.out.println("Invalid argument. Use -h for a valid list of arguments.");
					System.exit(1);
				}
			}
			try {
				threshold = -1;
				Scanner sc = new Scanner(System.in);
				String input;
				File[] pakFiles, wavFiles;
				while (true) {
					System.out.println("Enter a valid directory containing PAK files:");
					input = sc.nextLine();
					File pakDir = new File(input);
					if (pakDir.isDirectory()) //exclude ANM, EFF and Voice PAKs from the filter
					{
						pakFiles = pakDir.listFiles((dir,
						name) -> (name.toLowerCase().endsWith(".pak")
						&& !(name.toLowerCase().contains("anm") || name.toLowerCase().contains("eff") || name.toLowerCase().contains("voice"))));
						if (pakFiles.length == 0) System.out.println("Directory does NOT contain PAK files. Try again!\n");
						else break;
					} else System.out.println("Invalid directory. Try again!\n");
				}
				while (true) {
					System.out.println("Enter a valid directory containing WAV files:");
					input = sc.nextLine();
					File wavDir = new File(input);
					if (wavDir.isDirectory()) {
						wavFiles = wavDir.listFiles((dir, name) -> (name.toLowerCase().endsWith(".wav")));
						if (wavFiles.length == 0)
							System.out.println("Directory does NOT contain WAV files. Try again!\n");
						else break;
					}
				}
				while (threshold == -1) {
					System.out.print("Enter a dB threshold from 0 to 99: ");
					input = sc.nextLine();
					if (input.matches("\\d+") && input.length() <= 2) threshold = Integer.parseInt(input);
					else System.out.println("Invalid dB threshold. Try again!");
				}
				sc.close();

				long startLPS = System.currentTimeMillis();
				assignLpsToPak(pakFiles, wavFiles);
				String msg = "";
				if (hasNoValidWAVs) {
					msg += "No valid WAVs were detected. Make sure they follow this naming convention:\n"
					+ "X-YYY-ZZ.wav\nX ---> Name (of a character, menu, scenario etc.) WITHOUT special characters;\n"
					+ "YYY -> Number up to 3 digits (which will be used for the audio file ID);\n"
					+ "ZZ --> Region (either US or JP, which matters for character costumes or LPS PAKs from Dragon History).\n\n"
					+ "NOTE: The hyphens can also be replaced with underscores.\n";
				}
				if (validPaks == 0) {
					msg += "No valid PAKs were detected. Make sure they are either:\n"
					+ "a) Character Costume Files (that must end with" + '"' + "Xp.pak" + '"' + " or " + '"'
					+ "Xp_dmg.pak" + '"' + ", where X represents the costume number);\n"
					+ "b) Dragon History LPS PAKs, which use the following name convention:\nLPS-XX-B-YY.pak\n"
					+ "* XX -> Region (either US or JP);\n* YY -> Number up to 2 digits (which is the scenario ID).\n"
					+ "c) Non-character PAKs whose file names start with BT4.\n"
					+ "d) Literally any other PAK that contains LPS somewhere in its name, or ends with LIPS.\n";
				}
				System.out.println(msg);
				long endLPS = System.currentTimeMillis();
				long end = System.currentTimeMillis();
				System.out.println("\nTotal Execution Time: " + (end - start) / 1000.0 + " s");
				System.out.println("LIPS Automation Time: " + (endLPS - startLPS) / 1000.0 + " s");
			} catch (UnsupportedAudioFileException | IOException e) {
				setErrorLog(e);
			}
		} else gui.App.main(null);
	}
}