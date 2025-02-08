package cmd;
//Tenkaichi LPS Generator v1.3 by ViveTheModder
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

public class Main 
{
	public static boolean hasNoValidWAVs=false, wiiMode=false; //Wii Mode is also used for Raging Blast
	static boolean startsClosed=false; //condition of 1st keyframe (closed/true or open/false)
	public static int numPakContents, pakTotal=0, threshold=45, wavTotal=0;
	private static final String TITLE = "Tenkaichi LPS Generator v1.3";
	private static boolean isCharaCostumePak(File pakRef) throws IOException
	{
		RandomAccessFile pak = new RandomAccessFile(pakRef,"r");
		numPakContents = LittleEndian.getInt(pak.readInt());
		pak.seek((numPakContents+1)*4);
		int fileSize = LittleEndian.getInt(pak.readInt());
		int actualFileSize = (int) pak.length();
		pak.close();
		if (fileSize==actualFileSize && numPakContents>=250 && numPakContents<=252) return true;
		return false;
	}
	private static byte[] getLpsFileContents(short[] keyframes, int numKeyframes)
	{
		short fileSize = (short) (16+(numKeyframes*4));
		short mouthCondBoolean=1;
		if (startsClosed==true) mouthCondBoolean=0;
		//make lps file size the nearest multiple of 16
		if (fileSize%64!=0) fileSize = (short) (fileSize+64-(fileSize%64));
		byte[] lps = new byte[fileSize];
		byte[] LIPS = {0x4C,0x49,0x50,0x53};
		if (wiiMode) LIPS[3]=0x57; //change S to W if Wii/RB mode is enabled
		System.arraycopy(LIPS, 0, lps, 0, 4);
		System.arraycopy(LittleEndian.getByteArrayFromInt(2), 0, lps, 4, 4);
		System.arraycopy(LittleEndian.getByteArrayFromInt(numKeyframes), 0, lps, 8, 4);
		int pos=16;
		for (int i=0; i<numKeyframes; i++)
		{
			System.arraycopy(LittleEndian.getByteArrayFromShort(keyframes[i]), 0, lps, pos, 2);
			pos+=2;
			System.arraycopy(LittleEndian.getByteArrayFromShort((short) (mouthCondBoolean%2)), 0, lps, pos, 2);
			pos+=2; mouthCondBoolean++;
		}
		return lps;
	}
	private static short[] getLpsKeyframes(File wav) throws UnsupportedAudioFileException, IOException
	{
		ArrayList<Short> list = new ArrayList<>();
		AudioInputStream stream = AudioSystem.getAudioInputStream(wav);
		AudioFormat format = stream.getFormat();
		String mouthCond=""; //initial mouth condition (open or closed)
		String tempCond=""; //temporary mouth condition, used to skip duplicate conditions
		short prevFrame=-1; //previous frame, used to compare two consecutive frames
		long numFrameBytes = stream.getFrameLength();
		int numBytesPerFrame = format.getFrameSize();
		float duration =  numFrameBytes / format.getFrameRate();
		short audioFrames = (short) Math.round(duration*60);
		byte[] audioBytes = new byte[numBytesPerFrame];
		for (int i=0; i<numFrameBytes; i++) 
		{
			stream.read(audioBytes);
			long sampleSum=0;
			for (int j=0; j<numBytesPerFrame; j+=2)
			{
				short sample = (short) ((audioBytes[j] & 0xFF) | (audioBytes[j+1]<<8));
				sampleSum += sample*sample; //sum of squares
			}
			double rms = Math.sqrt((double)sampleSum/numBytesPerFrame); //root mean square
			if (i==0 && rms==0) startsClosed=true; //0 rms means the volume is -Infinity dB
			double db = 20 * Math.log10(rms); //decibels
			double time = i/24000.0; //current fraction of a second
			short currFrame = (short) Math.round(time*60);
			//test open & closed ranges by filtering the results (this could also be done with a boolean variable lol)
			if (db<threshold) mouthCond="Closed";
			else mouthCond="Open";
			if (prevFrame!=currFrame) 
			{
				if (!mouthCond.equals(tempCond)) list.add(currFrame);
				tempCond=mouthCond;
			}
			prevFrame = currFrame;
		}
		//as a security measure, add one last keyframe that is always closed 
		if (tempCond.equals("Open")) list.add(audioFrames);
		//manual way of doing list.toArray(), which traverses the list twice
		int numKeyframes = list.size();
		short[] keyframes = new short[numKeyframes];
		for (int i=0; i<numKeyframes; i++) keyframes[i] = list.get(i);
		return keyframes;
	}
	public static void assignLpsToPak(File[] pakFiles, File[] wavFiles) throws UnsupportedAudioFileException, IOException
	{
		int cnt=0;
		pakTotal = pakFiles.length; wavTotal = wavFiles.length;
		if (gui.App.bar!=null) gui.App.bar.setMaximum(pakTotal*wavTotal);
		for (File wav: wavFiles)
		{
			String wavName = wav.getName().replace(".wav", "");
			String[] wavNameArray = wavName.split("_");
			short[] keyframes = getLpsKeyframes(wav);
			short[] openMouthIntervals = new short[(keyframes.length-1)/2];
			int intervalIndex=0, displacement=0;
			
			for (int i=0; i<keyframes.length; i+=2)
			{
				if (startsClosed==true) displacement=1;
				if (intervalIndex<openMouthIntervals.length) 
					openMouthIntervals[intervalIndex] = (short) (keyframes[i+1+displacement]-keyframes[i+displacement]);
				intervalIndex++;
			}
			//filter keyframes based on open mouth intervals
			int numKeyframes = keyframes.length;
			for (int i=0; i<openMouthIntervals.length; i++)
			{
				int remainingKeyframesSize = numKeyframes-(i+displacement+3);
				if (openMouthIntervals[i]==1 && remainingKeyframesSize>1) //exclude keyframes with a difference/interval of 1 frame
				{
					short[] remainingKeyframes = new short[remainingKeyframesSize];
					if (i+4+remainingKeyframesSize<=numKeyframes) //prevent System.arraycopy() from softlocking the program
					{
						System.arraycopy(keyframes, i+4, remainingKeyframes, 0, remainingKeyframes.length);
						numKeyframes-=2;
						System.arraycopy(remainingKeyframes, 0, keyframes, i+displacement+1, remainingKeyframes.length);
					}
				}
			}
			byte[] lpsContents = getLpsFileContents(keyframes,numKeyframes);
			for (File pak: pakFiles)
			{
				int wavID;
				String pakName = pak.getName().toLowerCase();
				//check delimiter (underscore or hyphen/dash/minus)
				if (wavName.contains("_")) wavNameArray = wavName.split("_");
				else if (wavName.contains("-")) wavNameArray = wavName.split("-");
				else 
				{
					wavTotal--;
					if (wavTotal==0) hasNoValidWAVs=true;
					continue;
				}
				//check if name ends with US/JP (region) to then get the WAV ID (zero-indexed)
				if (wavName.endsWith("US") || wavName.endsWith("JP")) wavID = Integer.parseInt(wavNameArray[wavNameArray.length-2]);
				else wavID = Integer.parseInt(wavNameArray[wavNameArray.length-1]);
				//check if current PAK file is a costume file, then change the WAV ID based on the WAV's language
				if (isCharaCostumePak(pak))
				{
					String charaNameFromPak=null;
					if (pakName.endsWith("p.pak")) charaNameFromPak = pakName.substring(0, pakName.length()-7);
					else if (pakName.endsWith("p_dmg.pak")) charaNameFromPak = pakName.substring(0, pakName.length()-11);
					String charaNameFromWav = wavName.substring(0, wavName.length()-7).toLowerCase();
					int newWavID=wavID;
					if (wavNameArray[wavNameArray.length-1].endsWith("US")) newWavID=152+(wavID%500);
					else if (wavNameArray[wavNameArray.length-1].endsWith("JP")) newWavID=52+(wavID%500);
					if (numPakContents==250) newWavID-=4; //this only applies to Budokai Tenkaichi 2's character costumes
					//only overwrite current character costume PAK if the character name matches with the one from the WAV
					if (charaNameFromPak.equals(charaNameFromWav)) 
					{
						System.out.println("Assigning generated LPS (from "+wavName.toUpperCase()+".WAV) to "+pakName.toUpperCase()+"...");
						overwritePakFile(pak, lpsContents, newWavID); cnt+=2;
					}
				}
				else if (pakName.startsWith("lps") || pakName.endsWith("lps") || pakName.contains("lips"))
				{
					overwritePakFile(pak, lpsContents, wavID); cnt+=2;
				}
				//increment progress bar percentage
				if (gui.App.bar!=null) gui.App.bar.setValue(cnt);
			}
		}
	}
	private static void overwritePakFile(File pakRef, byte[] lpsContents, int id) throws IOException
	{
		RandomAccessFile pak = new RandomAccessFile(pakRef,"rw");
		int numSizes = LittleEndian.getInt(pak.readInt());
		int[] positions = new int[numSizes+1];
		int[] sizes = new int[numSizes];
		int tempPos=0;
		
		for (int i=1; i<=numSizes; i++) //initialize positions and sizes
		{
			if (tempPos==0) positions[i-1] = LittleEndian.getInt(pak.readInt()); //current position
			else positions[i-1] = tempPos;
			positions[i] = LittleEndian.getInt(pak.readInt()); //next position
			tempPos = positions[i]; //temporary position which exists to prevent reading the same positions twice
			sizes[i-1] = positions[i]-positions[i-1];
		}
		
		int difference = sizes[id]-lpsContents.length;
		if (difference!=0)
		{
			//fix index
			pak.seek((id+2)*4);
			for (int i=id+1; i<=numSizes; i++) pak.writeInt(LittleEndian.getInt(positions[i]-difference));
			pak.seek(positions[id+1]);
			int fullFileSize = (int)pak.length();
			int restOfFileSize = fullFileSize - positions[id+1];
			byte[] restOfFile = new byte[restOfFileSize];
			pak.read(restOfFile); //copy the rest of the file contents before overwriting
			pak.seek(positions[id]);
			//actual overwriting process
			pak.write(lpsContents);
			pak.write(restOfFile);
			pak.setLength(fullFileSize-difference);
			pak.close();
		}
		else
		{
			pak.seek(positions[id]);
			pak.write(lpsContents);
		}
	}
	public static void setErrorLog(Exception e)
	{
		File errorLog = new File("errors.log");
		try {
			FileWriter logWriter = new FileWriter(errorLog,true);
			logWriter.append(new SimpleDateFormat("dd-MM-yy-hh-mm-ss").format(new Date())+":\n"+e.getClass().getSimpleName()+": "+e.getMessage()+"\n");
			logWriter.close();
			Desktop.getDesktop().open(errorLog);
		} catch (IOException e1) {
			e1.printStackTrace();
		}
		System.exit(1);
	}
	public static void main(String[] args) 
	{
		long start = System.currentTimeMillis();
		if (args.length>0)
		{
			if (args[0].equals("-w") || args[0].equals("-wii")) wiiMode=true;
			else if (args[0].equals("-p") || args[0].equals("-ps2")) wiiMode=false;
			else if (args[0].equals("-h") || args[0].equals("-help"))
			{
				System.out.println(TITLE+"\nAutomatically generate Lip-Syncing files and assign them to PAK files (for characters or menus).\n"
				+ "Here is a list of the only arguments that can be used. Use -h or -help to print this out again.\n\n"
				+ "* -w (or -wii)\nEnable support for PAK files that use the Big Endian byte order (from Wii BT2/BT3 or PS3 RB1/RB2).\n"
				+ "* -p (or -ps2)\nEnable support for PAK files that use the Little Endian byte order (from PS2 BT2/BT3).\n");
				System.exit(0);
			}
			else 
			{
				System.out.println("Invalid argument. Use -h for a valid list of arguments.");
				System.exit(1);
			}
			try 
			{
				Scanner sc = new Scanner(System.in);
				String input;
				File[] pakFiles, wavFiles;
				while (true)
				{
					System.out.println("Enter a valid directory containing PAK files:");
					input = sc.nextLine();
					File pakDir = new File(input);
					if (pakDir.isDirectory()) //exclude ANM, EFF and Voice PAKs from the filter
					{
						pakFiles = pakDir.listFiles((dir, name) -> 
						(name.toLowerCase().endsWith(".pak") && 
						!(name.toLowerCase().contains("anm") || name.toLowerCase().contains("eff") || name.toLowerCase().contains("voice"))));
						if (pakFiles.length==0) System.out.println("Directory does NOT contain PAK files. Try again!\n");
						else break;
					}
					else System.out.println("Invalid directory. Try again!\n");
				}
				while (true)
				{
					System.out.println("Enter a valid directory containing WAV files:");
					input = sc.nextLine();
					File wavDir = new File(input);
					if (wavDir.isDirectory())
					{
						wavFiles = wavDir.listFiles((dir, name) -> (name.toLowerCase().endsWith(".wav")));
						if (wavFiles.length==0) System.out.println("Directory does NOT contain WAV files. Try again!\n");
						else break;
					}
				}
				sc.close();
				
				long startLPS = System.currentTimeMillis();
				assignLpsToPak(pakFiles, wavFiles);
				if (hasNoValidWAVs) 
				{
					System.out.println("No valid WAVs were detected. Make sure they follow this naming convention:\n"
					+ "X-YYY-ZZ.wav\nX ---> Name (of a character, menu, scenario etc.);\n"
					+ "YYY -> Number up to 3 digits (which will be used for the audio file ID);\n"
					+ "ZZ --> Region (either US or JP; this only really matters for the character costume files).");
				}
				long endLPS = System.currentTimeMillis();
				long end = System.currentTimeMillis();
				System.out.println("\nTotal Execution Time: "+(end-start)/1000.0+" s");
				System.out.println("LIPS Automation Time: "+(endLPS-startLPS)/1000.0+" s");
			} 
			catch (UnsupportedAudioFileException | IOException e) 
			{
				setErrorLog(e);
			}
		}
		else gui.App.main(null);
	}
}