
package restless.github;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Toolkit;
import java.awt.geom.AffineTransform;
import java.awt.image.AffineTransformOp;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.URL;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

import javax.imageio.ImageIO;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.WindowConstants;
import javax.swing.border.LineBorder;

import org.eclipse.jgit.api.AddCommand;
import org.eclipse.jgit.api.CommitCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.InitCommand;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.PersonIdent;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;

/**
 * This program hacks your GitHub Contributions Calendar to match an image.
 * <p>
 * More specifically, given an image file and GitHub username as input, it
 * creates and populates a dummy Git repository, which when pushed to that
 * user's public space will result in that user's Contributions Calendar
 * matching the specified image.
 * </p>
 */
public class ContribHacker {

	// -- Constants --

	private static final String JAR_NAME =
		"contrib-hacker-1.0.0-SNAPSHOT-jar-with-dependencies.jar";

	private static final String PROJECT_URL =
		"https://github.com/ctrueden/commit-hacker";

	private static final String COMMIT_NOTICE =
		"\n\nThis is an autogenerated commit; see:\n" +
		"    " + PROJECT_URL;

	private static final String USER_TOKEN = "USER";
	private static final String CALENDAR_URL = "https://github.com/users/" +
		USER_TOKEN + "/contributions_calendar_data";

	private static final SimpleDateFormat GITHUB_DATE_FORMAT =
		new SimpleDateFormat("yyyy/MM/dd");

	private static final SimpleDateFormat SHORT_DATE_FORMAT =
		new SimpleDateFormat("yyyyMMdd");

	private static final int CAL_WIDTH = 54;
	private static final int CAL_HEIGHT = 7;

	private static final String CAL_ASCII = ".-+#";

	private static final Color[] CAL_COLORS = {
		new Color(214, 230, 133),
		new Color(140, 198, 101),
		new Color(68, 163, 64),
		new Color(30, 104, 35)
	};

	private static final String ASCII_IMAGE_FILE = "image.txt";
	private static final String CALENDAR_DATA_FILE = "data.txt";

	// -- Command line arguments (thank you args4j!) --

	@Option(name = "-i", aliases = {"--image"},
		usage = "the target image file")
	private File imageFile = new File("samples/hello-world.png");//TEMP

	@Option(name = "-u", aliases = {"--github-user"},
		usage = "the GitHub username")
	private String githubUser = "ctrueden";//TEMP

	@Option(name = "-o", aliases = {"--output-dir"},
		usage = "the directory where the Git repository should be populated")
	private File gitDir;

	@Option(name = "-g", aliases = {"--gui"},
		usage = "display progress in a graphical window")
	private boolean showGUI = true;//TEMP

	@Option(name = "-d", aliases = {"--debug"}, usage = "debug mode")
	private boolean debug = true;//TEMP

	// -- Fields --

	private Contrib[][] contrib;
	private int maxContrib;

	private Git git;
	private File asciiImageFile;
	private File calendarDataFile;
	private PersonIdent author;
	private JFrame progressFrame;
	private JProgressBar progressBar;

	// -- Main method --

	public static void main(final String[] args) throws Exception {
		final ContribHacker contribHacker = new ContribHacker();
		contribHacker.parseArgs(args);
		contribHacker.execute();
	}

	// -- ContribHacker methods --

	/** Parses the command line arguments, populating the instance fields. */
	public void parseArgs(final String[] args) {
		final CmdLineParser parser = new CmdLineParser(this);

		try {
			parser.parseArgument(args);

			if (imageFile == null) {
				throw new CmdLineException(parser, "No image file given");
			}
			if (githubUser == null) {
				throw new CmdLineException(parser, "No GitHub user given");
			}
		}
		catch (final CmdLineException e) {
			System.err.println(e.getMessage());
			System.err.println();

			// print usage instructions
			System.err.println("java -jar " + JAR_NAME +
				" [options...] arguments...");

			// print the list of available options
			parser.printUsage(System.err);
			System.err.println();

			System.exit(1);
		}
	}

	/** Generates the Git repository according to the current settings. */
	public void execute() throws IOException, ParseException, GitAPIException {
		final int[][] pix = readPixels();
		readContributions();
		computeTargetContrib(pix);

		if (debug) {
			debug(asciiImage(true));
			printContrib();
		}

		initGitRepository();

		if (showGUI) showProgressWindow();

		int i = 0;
		for (int x = 0; x < CAL_WIDTH; x++) {
			for (int y = 0; y < CAL_HEIGHT; y++) {
				if (contrib[y][x] == null) continue;
				while (contrib[y][x].current < contrib[y][x].target) {
					contrib[y][x].current++;
					if (git != null) doCommit(y, x);
					i++;
					if (showGUI) updateProgress(i);
				}
			}
		}

		progressFrame.dispose();

		if (debug) System.out.println("Complete!");
	}

	// -- Helper methods - setup --

	/** Reads the given image file, converting it to 54x7, 2-bit grayscale. */
	private int[][] readPixels() throws IOException {
		if (!imageFile.exists()) {
			System.err.println("No such image: " + imageFile.getPath());
			System.exit(2);
		}

		// read and scale input image
		final BufferedImage image =
			scale(ImageIO.read(imageFile), CAL_WIDTH, CAL_HEIGHT);

		// convert to 2-bit grayscale data
		final int[][] pix = new int[CAL_HEIGHT][CAL_WIDTH];
		for (int y = 0; y < CAL_HEIGHT; y++) {
			for (int x = 0; x < CAL_WIDTH; x++) {
				final int rgb = image.getRGB(x, y);
				// CTR CHECK
				final int r = 0xff & rgb;
				final int g = 0xff & (rgb >> 8);
				final int b = 0xff & (rgb >> 16);

				// convert RGB to 8-bit grayscale
				final int gray = (int) Math.round(0.299 * r + 0.587 * g + 0.114 * b);

				// convert 8-bit to 2-bit (4 possible values)
				pix[y][x] = gray / 64;
			}
		}
		return pix;
	}

	/** Rescales the given image to the specified width and height. */
	private BufferedImage scale(final BufferedImage input,
		final int newWidth, final int newHeight)
	{
		final int oldWidth = input.getWidth(), oldHeight = input.getHeight();
		if (oldWidth == newWidth && oldHeight == newHeight) return input;

		final BufferedImage output =
			new BufferedImage(newWidth, newHeight, input.getType());
		final AffineTransform at = new AffineTransform();
		at.scale((double) newWidth / oldWidth, (double) newHeight / oldHeight);
		final AffineTransformOp scaleOp =
			new AffineTransformOp(at, AffineTransformOp.TYPE_BILINEAR);
		return scaleOp.filter(input, output);
	}

	/**
	 * Reads the Contributions Calendar of the given GitHub user into the
	 * {@link #contrib} field.
	 */
	private void readContributions() throws IOException, ParseException {
		contrib = new Contrib[CAL_HEIGHT][CAL_WIDTH];

		final URL url = new URL(CALENDAR_URL.replaceFirst(USER_TOKEN, githubUser));
		final String contents = readContents(url);

		int y = -1, x = 0;
		for (final String token : contents.split("\\],\\[")) {
			final String[] t = token.split(",");
			final String dateString = t[0];
			final String count = t[1].replaceAll("[^\\d]", "");
			final Date date = asDate(dateString);
			if (y < 0) {
				// determine initial offset
				final Calendar calendar = Calendar.getInstance();
				calendar.setTime(date);
				y = calendar.get(Calendar.DAY_OF_WEEK) - 1;
			}

			contrib[y][x] = new Contrib(date, Integer.parseInt(count));

			y++;
			if (y > 6) {
				// move to the next week
				y = 0;
				x++;
			}
		}
	}

	/** Gets the contents of the given URL as a string. */
	private String readContents(final URL url) throws IOException {
		final BufferedReader in =
			new BufferedReader(new InputStreamReader(url.openStream()));
		final StringBuilder sb = new StringBuilder();
		while (true) {
			final String line = in.readLine();
			if (line == null) break; // eof
			sb.append(line);
		}
		in.close();
		return sb.toString();
	}

	/**
	 * Computes a modified contributions table based on the existing one and
	 * desired final image.
	 */
	private void computeTargetContrib(final int[][] pix) {
		// NB: Computation could certainly be made more efficient, but this
		// method is performant enough and hopefully easy to understand.

		// compute minimum scale factor needed for desired image
		int scale = 1;
		for (int y = 0; y < CAL_HEIGHT; y++) {
			for (int x = 0; x < CAL_WIDTH; x++) {
				if (contrib[y][x] == null) continue;
				while (contrib[y][x].current > scale(pix[y][x], scale)) {
					scale++;
				}
			}
		}
		debug("Scale factor = " + scale);

		// populate new contribution matrix
		maxContrib = scale(0, scale);
		boolean maxFulfilled = false;
		for (int y = 0; y < CAL_HEIGHT; y++) {
			for (int x = 0; x < CAL_WIDTH; x++) {
				if (contrib[y][x] == null) continue;
				final int target = scale(pix[y][x], scale, maxFulfilled);
				if (target == maxContrib) maxFulfilled = true;
				contrib[y][x].target = target;
			}
		}
	}

	/**
	 * Scales the given inverted pixel value by the specified scale factor.
	 * 
	 * @param pixel The pixel value (0=strongest, 3=weakest)
	 * @param scale The scale factor
	 */
	private int scale(final int pixel, final int scale) {
		return scale * (4 - pixel);
	}

	/**
	 * Scales the given inverted pixel value by the specified scale factor.
	 * 
	 * @param pixel The pixel value (0=strongest, 3=weakest)
	 * @param scale The scale factor
	 * @param maxFulfilled Whether we have already scaled a maximum intensity
	 *          pixel. This matters because the first maximum intensity must be
	 *          scaled to the maximum value (e.g., with a scale factor of 15, one
	 *          result must be 60 to ensure everything is binned properly from
	 *          1-15, 16-30, 31-45, and 46-60). But once we have a single maximum
	 *          value in the picture, the remaining pixels can be scaled to the
	 *          minimum value of their desired bins, respectively (e.g.,
	 *          subsequent results can be set to 46 rather than 60 and still
	 *          appear as maximum intensity pixels).
	 */
	private int
		scale(final int pixel, final int scale, final boolean maxFulfilled)
	{
		if (pixel > 0 || maxFulfilled) {
			// cheating is OK!
			return scale * (3 - pixel) + 1;
		}
		// this is the first maximum intensity pixel; we cannot optimize
		return scale * (4 - pixel);
	}

	// -- Helper methods - miscellaneous --

	/**
	 * Generates an ASCII image string.
	 * 
	 * @param target If true, the image matches the target contributions; if
	 *          false, the image matches the current ones.
	 */
	private String asciiImage(final boolean target) {
		final String newLine = System.getProperty("line.separator");
		final StringBuilder sb = new StringBuilder();
		final int step = maxContrib / 4;
		for (int y = 0; y < CAL_HEIGHT; y++) {
			for (int x = 0; x < CAL_WIDTH; x++) {
				if (contrib[y][x] == null) {
					sb.append(" ");
					continue;
				}
				final int value =
					target ? contrib[y][x].target : contrib[y][x].current;
				final int index = (value - 1) / step;
				sb.append(CAL_ASCII.charAt(index));
			}
			sb.append(newLine);
		}
		return sb.toString();
	}

	/** Converts the given date string to a {@link Date} object. */
	private Date asDate(final String date) throws ParseException {
		final int quote = date.indexOf("\"");
		final String unwrappedDate = quote < 0 ? date :
			date.substring(quote + 1, date.lastIndexOf("\""));
		return GITHUB_DATE_FORMAT.parse(unwrappedDate);
	}

	// -- Helper methods - Git --

	/** Initializes the Git repository. */
	private void initGitRepository() throws IOException, GitAPIException {
		if (gitDir == null) {
			System.err.println("Warning: no output directory "
				+ "given for git repository; simulating result");
			return;
		}

		if (!gitDir.exists()) {
			final boolean success = gitDir.mkdirs();
			if (!success) {
				throw new IOException("Could not create Git output directory: " +
					gitDir);
			}
		}

		final InitCommand init = Git.init();
		init.setDirectory(gitDir);
		init.call();

		git = Git.open(gitDir);

		author = new PersonIdent(git.getRepository());

		asciiImageFile = new File(gitDir, ASCII_IMAGE_FILE);
		calendarDataFile = new File(gitDir, CALENDAR_DATA_FILE);
	}

	/** Updates the repository files and makes a commit. */
	private void doCommit(final int y, final int x)
		throws FileNotFoundException, GitAPIException
	{
		// update files
		updateImageFile();
		updateDataFile();

		// add files to changeset
		final AddCommand add = git.add();
		add.addFilepattern(ASCII_IMAGE_FILE);
		add.addFilepattern(CALENDAR_DATA_FILE);
		add.call();

		// commit the changes
		final CommitCommand commit = git.commit();
		commit.setAuthor(new PersonIdent(author, contrib[y][x].date));
		final String message = "(" + y + ", " + x + ") -> " +
			contrib[y][x].current + COMMIT_NOTICE;
		commit.setMessage(message);
		commit.call();
	}

	/** Writes an updated ASCII image file to disk. */
	private void updateImageFile() throws FileNotFoundException {
		final PrintWriter out = new PrintWriter(asciiImageFile);
		out.print(asciiImage(false));
		out.close();
	}

	/** Writes an updated calendar data file to disk. */
	private void updateDataFile() throws FileNotFoundException {
		final PrintWriter out = new PrintWriter(calendarDataFile);
		for (int x = 0; x < CAL_WIDTH; x++) {
			for (int y = 0; y < CAL_HEIGHT; y++) {
				if (contrib[y][x] == null ||
					contrib[y][x].current - contrib[y][x].initial == 0)
				{
					continue;
				}
				out.println(contrib[y][x]);
			}
		}
		out.close();
	}

	// -- Helper methods - GUI --

	/** Displays the graphical progress window. */
	private void showProgressWindow() {
		// compute total number of iterations
		int total = 0;
		for (int x = 0; x < CAL_WIDTH; x++) {
			for (int y = 0; y < CAL_HEIGHT; y++) {
				if (contrib[y][x] == null) continue;
				total += contrib[y][x].target - contrib[y][x].current;
			}
		}

		final JPanel calendarPane = new JPanel() {

			private final int tileSize = 12, tileTotal = 15;
			private final Dimension prefSize =
				new Dimension(CAL_WIDTH * tileTotal, CAL_HEIGHT * tileTotal);

			@Override
			public void paint(final Graphics g) {
				super.paint(g);
				final int step = maxContrib / 4;
				for (int y = 0; y < CAL_HEIGHT; y++) {
					for (int x = 0; x < CAL_WIDTH; x++) {
						if (contrib[y][x] == null) continue;
						final int colorIndex = (contrib[y][x].current - 1) / step;
						g.setColor(CAL_COLORS[colorIndex]);
						g.fillRect(tileTotal * x, tileTotal * y, tileSize, tileSize);
					}
				}
			}

			@Override
			public Dimension getPreferredSize() {
				return prefSize;
			}

		};
		calendarPane.setBorder(new LineBorder(Color.red));

		progressBar = new JProgressBar();
		progressBar.setMaximum(total);
		progressBar.setStringPainted(true);

		final JPanel contentPane = new JPanel();
		contentPane.setLayout(new BorderLayout());
		contentPane.add(calendarPane, BorderLayout.CENTER);
		contentPane.add(progressBar, BorderLayout.SOUTH);

		progressFrame = new JFrame("Contribution Hacker");
		progressFrame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
		progressFrame.setContentPane(contentPane);
		progressFrame.pack();

		// center the window onscreen
		final Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
		final Dimension windowSize = progressFrame.getSize();
		final int posX = (screenSize.width - windowSize.width) / 2;
		final int posY = (screenSize.height - windowSize.height) / 2;
		progressFrame.setLocation(posX, posY);

		progressFrame.setVisible(true);
	}

	/** Updates the graphical progress window. */
	private void updateProgress(final int value) {
		// update progress bar
		progressBar.setValue(value);
		final int previous = 100 * (value - 1) / progressBar.getMaximum();
		final int current = 100 * value / progressBar.getMaximum();
		if (previous != current) progressBar.setString("" + current + "%");

		// redraw calendar graphic
		progressFrame.repaint();
	}

	// -- Helper methods - debugging --

	/** Emits the given message to stdout, if the debug flag is set. */
	private void debug(final String msg) {
		if (debug) System.out.println(msg);
	}

	/** Debugging method for outputting the contributions table. */
	private void printContrib() {
		System.out.println("-- Current Contributions --");
		for (int y = 0; y < contrib.length; y++) {
			for (int x = 0; x < contrib[y].length; x++) {
				String s = contrib[y][x] == null ? "-" : ("" + contrib[y][x].current);
				while (s.length() < 4)
					s = " " + s;
				System.out.print(s);
			}
			System.out.println();
		}
		System.out.println();
		System.out.println("-- Target Contributions --");
		for (int y = 0; y < contrib.length; y++) {
			for (int x = 0; x < contrib[y].length; x++) {
				String s = contrib[y][x] == null ? "-" : ("" + contrib[y][x].target);
				while (s.length() < 4)
					s = " " + s;
				System.out.print(s);
			}
			System.out.println();
		}
	}

	// -- Helper classes --

	/** Data structure for tracking contributions. */
	private static class Contrib {

		private Date date;
		private int initial;
		private int current;
		private int target;

		private Contrib(final Date date, final int initial) {
			this.date = date;
			this.initial = current = initial;
		}

		@Override
		public String toString() {
			return SHORT_DATE_FORMAT.format(date) + " " + (current - initial);
		}

	}

}
