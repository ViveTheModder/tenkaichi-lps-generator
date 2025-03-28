package gui;
//Tenkaichi LPS Generator v1.5 by ViveTheModder
import java.awt.Color;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Image;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.SwingWorker;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;
import javax.swing.WindowConstants;

import cmd.Main;

public class App 
{
	private static final Toolkit DEF_TOOLKIT = Toolkit.getDefaultToolkit();
	private static final Font BOLD = new Font("Tahoma", 1, 24);
	private static final Font BOLD_S = new Font("Tahoma", 1, 12);
	private static final Image ICON = DEF_TOOLKIT.getImage(ClassLoader.getSystemResource("img/icon.png"));
	private static final String HTML_A_START = "<html><a href=''>";
	private static final String HTML_A_END = "</a></html>";
	private static final String HTML_DIV_START = "<html><div style='font-weight: bold; font-size: 12px;'>";
	private static final String HTML_DIV_CENTER = "<html><div style='text-align: center;'>";
	private static final String HTML_DIV_END = "</div></html>";
	private static final String WINDOW_TITLE = "Tenkaichi LPS Generator v1.5";
	private static final String[] FILE_TYPES = {"PAK","WAV"};
	private static File[] pakFiles, wavFiles;
	public static JProgressBar bar;
	
	private static File getFolderFromFileChooser(int index)
	{
		File folder=null;
		JFileChooser chooser = new JFileChooser();
		chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
		chooser.setDialogTitle("Select Folder with "+FILE_TYPES[index]+" files...");
		while (true)
		{
			int result = chooser.showOpenDialog(chooser);
			if (result==0)
			{
				File tempFolderRef = chooser.getSelectedFile(); //actually gets the selected folder
				File[] tempFolderCSVs = tempFolderRef.listFiles((dir, name) -> 
				{
					String nameLower = name.toLowerCase();
					if (index==0) //exclude ANM, EFF and Voice PAKs from the PAK filter
					{
						return nameLower.endsWith(".pak") && 
						!(nameLower.contains("anm") || nameLower.contains("eff") || nameLower.contains("voice"));
					}
					return nameLower.endsWith("."+FILE_TYPES[index].toLowerCase());
				});
				if (!(tempFolderCSVs==null || tempFolderCSVs.length==0)) 
				{
					folder = tempFolderRef; break;
				}
				else 
				{
					errorBeep();
					JOptionPane.showMessageDialog(chooser, "This folder does NOT have "+FILE_TYPES[index]+" files! Try again!", WINDOW_TITLE, 0);
				}
			}
			else return folder;
		}
		return folder;
	}
	private static void errorBeep()
	{
		Runnable runWinErrorSnd = (Runnable) DEF_TOOLKIT.getDesktopProperty("win.sound.exclamation");
		if (runWinErrorSnd!=null) runWinErrorSnd.run();
	}
	private static void setApplication()
	{
		//initialize components
		Box threshold = Box.createHorizontalBox();
		Box title = Box.createHorizontalBox();
		Dimension thresholdFieldSize = new Dimension(50,25);
		GridBagConstraints gbc = new GridBagConstraints();
		Image img = ICON.getScaledInstance(90, 90, Image.SCALE_SMOOTH);
		ImageIcon imgIcon = new ImageIcon(img);
		JButton btn = new JButton("Apply Automatic Lip-Sync");
		JCheckBox wiiCheck = new JCheckBox(HTML_DIV_START+"Wii/PS3 Mode"+HTML_DIV_END);
		JFrame frame = new JFrame(WINDOW_TITLE);
		JLabel iconLabel = new JLabel(" ");
		JLabel thresholdLabel = new JLabel(HTML_DIV_START+"Threshold (dB):"+HTML_DIV_END);
		JLabel titleLabel = new JLabel("<html><div style='text-align: center; color: orange;'>"+WINDOW_TITLE.replace("S G", "S<br>G")+"</div></html>");
		JPanel panel = new JPanel();
		JMenuBar menuBar = new JMenuBar();
		JMenu fileMenu = new JMenu("File");
		JMenu helpMenu = new JMenu("Help");
		JMenuItem about = new JMenuItem("About");
		JMenuItem[] folderSelects = new JMenuItem[2];
		JTextField thresholdField = new JTextField();
		//set component properties
		gbc.gridwidth = GridBagConstraints.REMAINDER;
		iconLabel.setIcon(imgIcon);
		panel.setLayout(new GridBagLayout());
		thresholdField.setHorizontalAlignment(JTextField.CENTER);
		thresholdField.setMinimumSize(thresholdFieldSize);
		thresholdField.setMaximumSize(thresholdFieldSize);
		thresholdField.setPreferredSize(thresholdFieldSize);
		thresholdLabel.setToolTipText("<html><div style='text-align: center;'>"
		+ "A fixed volume level. If exceeded by any frame of the WAV,<br>"
		+ "it determines the character is talking during that frame."
		+ "<br>If not specified, the threshold will be set to 45 dB."
		+ "<br><br>If dealing with WAV files from post-ADX games (which no longer"
		+ "<br>use the ADX format), lowering the threshold is strongly encouraged."
		+ "<br>Otherwise, for games of the Budokai, Tenkaichi or Raging Blast series,"
		+ "<br>it is recommended to leave the threshold as is."+HTML_DIV_END);
		titleLabel.setFont(BOLD);
		wiiCheck.setToolTipText(HTML_DIV_CENTER+"This option is meant for files from the Wii version of Budokai Tenkaichi 2 & 3, and"
		+ "<br>the PS3 version of Raging Blast 1 & 2, whose integers are in Big Endian, not Little Endian<br>"
		+ "(which is the default byte order for the PS2 version of Budokai Tenkaichi 3)."+HTML_DIV_END);
		wiiCheck.setHorizontalAlignment(SwingConstants.CENTER);

		for (int i=0; i<2; i++)
		{
			final int index=i;
			folderSelects[i] = new JMenuItem("Select Folder with "+FILE_TYPES[i]+" files...");
			folderSelects[i].addActionListener(new ActionListener() 
			{
				@Override
				public void actionPerformed(ActionEvent e) 
				{
					File folder = getFolderFromFileChooser(index);
					if (folder==null) return;
					if (index==0) //exclude ANM, EFF and Voice PAKs from the filter 
					{
						pakFiles = folder.listFiles((dir, name) -> 
						(name.toLowerCase().endsWith(".pak") && 
						!(name.toLowerCase().contains("anm") || name.toLowerCase().contains("eff") || name.toLowerCase().contains("voice"))));
					}
					else wavFiles = folder.listFiles((dir, name) -> (name.toLowerCase().endsWith(".wav")));
				}
			});
			fileMenu.add(folderSelects[i]);
		}
		folderSelects[0].setToolTipText(HTML_DIV_CENTER+"ANM, EFF and Voice_US/Voice_JP PAK files will be excluded from the folder."+HTML_DIV_END);
		folderSelects[1].setToolTipText(HTML_DIV_CENTER+"Although the tool works for WAV files of any sample rates,<br>"
		+ "a sample rate of 24000 Hz is strongly encouraged."+HTML_DIV_END);
		helpMenu.add(about);
		about.addActionListener(new ActionListener()
		{
			@Override
			public void actionPerformed(ActionEvent e) 
			{
				Box[] boxes = new Box[3];
				Box mainBox = Box.createVerticalBox();
				String[] authorLinks = {"https://github.com/ViveTheModder","https://www.youtube.com/@nero_149","https://www.youtube.com/@pxgamer13"};
				String[] text = {"Made by: ","Initial LPS research done by: ","Greatly inspired (with spite) by: "};
				JLabel[] authors = 
				{new JLabel(HTML_A_START+"ViveTheModder"+HTML_A_END), new JLabel(HTML_A_START+"Nero149"+HTML_A_END), new JLabel(HTML_A_START+"Pxgamer13"+HTML_A_END)};
			
				for (int i=0; i<authors.length; i++)
				{
					final int index=i;
					boxes[i] = Box.createHorizontalBox();
					JLabel textLabel = new JLabel(text[i]);
					textLabel.setFont(BOLD_S);
					authors[i].setFont(BOLD_S);
					boxes[i].add(textLabel);
					boxes[i].add(authors[i]);
					authors[i].addMouseListener(new MouseAdapter() {
						@Override
						public void mouseClicked(MouseEvent e) {
							try {
								Desktop.getDesktop().browse(new URI(authorLinks[index]));
							} catch (IOException | URISyntaxException e1) {
								Main.setErrorLog(e1);
							}
						}});
					mainBox.add(boxes[i]);
				}
				JOptionPane.showMessageDialog(null, mainBox, WINDOW_TITLE, 1, imgIcon);
			}
		});
		thresholdField.addKeyListener(new KeyAdapter()
		{
			public void keyTyped(KeyEvent e)
			{
				char ch = e.getKeyChar();
				String text = thresholdField.getText();
				if (text.length()>1)
				{
					if (!(ch==KeyEvent.VK_DELETE || ch==KeyEvent.VK_BACK_SPACE))
						e.consume();
				}
				if (!(ch>='0' && ch<='9')) e.consume();
			}
		});
		btn.addActionListener(new ActionListener()
		{
			@Override
			public void actionPerformed(ActionEvent e) 
			{
				String errorMsg="";
				if (pakFiles==null || pakFiles.length==0) errorMsg+="No directory for "+FILE_TYPES[0]+" has been selected!\n";
				if (wavFiles==null || wavFiles.length==0) errorMsg+="No directory for "+FILE_TYPES[1]+" has been selected!\n";
				if (!errorMsg.equals("")) 
				{
					errorBeep();
					JOptionPane.showMessageDialog(null, errorMsg, WINDOW_TITLE, 0);
				}
				else
				{
					String thresholdText = thresholdField.getText();
					if (!thresholdText.equals("")) cmd.Main.threshold = Integer.parseInt(thresholdField.getText());
					frame.setVisible(false); frame.dispose();
					if (wiiCheck.isSelected()) cmd.Main.wiiMode=true;
					setProgress();
				}
			}
		});
		
		//add components
		menuBar.add(fileMenu);
		menuBar.add(helpMenu);
		threshold.add(thresholdLabel);
		threshold.add(new JLabel(" "));
		threshold.add(thresholdField);
		title.add(iconLabel);
		title.add(titleLabel);
		panel.add(title,gbc);
		panel.add(threshold,gbc);
		panel.add(wiiCheck,gbc);
		panel.add(btn,gbc);
		frame.add(panel);
		//set frame properties
		frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
		frame.setIconImage(ICON);
		frame.setJMenuBar(menuBar);
		frame.setLocationRelativeTo(null);
		frame.setSize(512,256);
		frame.setVisible(true);
	}
	private static void setProgress()
	{
		//change progress bar settings (must be done before declaring)
	    UIManager.put("ProgressBar.background", Color.WHITE);
	    UIManager.put("ProgressBar.foreground", Color.GREEN);
	    UIManager.put("ProgressBar.selectionBackground", Color.BLACK);
	    UIManager.put("ProgressBar.selectionForeground", Color.BLACK);
	    //initialize components
	    Dimension barSize = new Dimension(256,32);
	    GridBagConstraints gbc = new GridBagConstraints();
	    JDialog progress = new JDialog();
		JPanel panel = new JPanel();
		JLabel label = new JLabel("Assigning generated LPS...");
		bar = new JProgressBar();
		//set component properties
		bar.setValue(0);
		bar.setStringPainted(true);
		bar.setBorderPainted(true);
		bar.setBorder(BorderFactory.createMatteBorder(2, 2, 2, 2, Color.LIGHT_GRAY));
		bar.setFont(BOLD_S);
		bar.setMinimumSize(barSize);
		bar.setMaximumSize(barSize);
		bar.setPreferredSize(barSize);
		gbc.gridwidth = GridBagConstraints.REMAINDER;
		label.setFont(BOLD);
		label.setHorizontalAlignment(SwingConstants.CENTER);
	    panel.setLayout(new GridBagLayout());
		//add components
		panel.add(label,gbc);
		panel.add(new JLabel(" "),gbc);
		panel.add(bar,gbc);
	    progress.add(panel);
	    progress.setTitle(WINDOW_TITLE);
	    progress.setSize(512,256);
	    progress.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
	    progress.setIconImage(ICON);
	    progress.setLocationRelativeTo(null);
	    progress.setVisible(true);
	    
	    SwingWorker<Void, Void> worker = new SwingWorker<Void, Void>()
		{
			@Override
			protected Void doInBackground() throws Exception 
			{
				long start = System.currentTimeMillis();
				cmd.Main.assignLpsToPak(pakFiles, wavFiles);
				long end = System.currentTimeMillis();
				double time = (end-start)/1000.0;
				progress.setVisible(false); 
				progress.dispose();
				String msg="";
				int msgType=1;
				if (cmd.Main.hasNoValidWAVs) 
				{
					msg += "No valid WAVs were detected. Make sure they follow this naming convention:\n"
					+ "X-YYY-ZZ.wav\nX ---> Name (of a character, menu, scenario etc.);\n"
					+ "YYY -> Number up to 3 digits (which will be used for the audio file ID);\n"
					+ "ZZ --> Region (either US or JP, which matters for character costumes or LPS PAKs from Dragon History).\n";
					msgType=0;
				}
				if (cmd.Main.validPaks==0)
				{
					msg += "No valid PAKs were detected. Make sure they are either:\n"
					+ "a) Character Costume Files (that must end with"+'"'+"Xp.pak"+'"'+" or "+'"'+"Xp_dmg.pak"+'"'+", where X represents the costume number);\n"
					+ "b) Dragon History LPS PAKs, which uses the following name convention:\nLPS-XX-B-YY.pak\n"
					+ "* XX -> Region (either US or JP);\n* YY -> Number up to 2 digits (which is the scenario ID).\n"
					+ "c) Literally any other PAK that starts/ends with LPS, or contains LIPS somewhere in its name.\n";
					msgType=0;
				}
				if (msg.equals("")) msg="Automatic Lip-Syncing of "+Main.wavTotal+" WAV files applied to "+Main.pakTotal+" PAK files in "+time+" s!";
				if (msgType==1) DEF_TOOLKIT.beep();
				else errorBeep();
				JOptionPane.showMessageDialog(null, msg, WINDOW_TITLE, msgType);
				return null;
			}
		};
		worker.execute();
	}
	public static void main(String[] args) 
	{
		try 
		{
			UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
			setApplication();
		}
		catch (ClassNotFoundException | InstantiationException | IllegalAccessException | UnsupportedLookAndFeelException e) 
		{
			Main.setErrorLog(e);
		}
	}
}