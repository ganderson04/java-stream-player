package com.goxr3plus.streamplayer.tools;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;

import org.jaudiotagger.audio.mp3.MP3File;

import com.goxr3plus.streamplayer.enums.AudioType;

public final class TimeTool {

	private TimeTool() {
	}

	/**
	 * Returns the time in format %02d:%02d.
	 *
	 * @param seconds the seconds
	 * @return the time edited on hours
	 */
	public static String getTimeEditedOnHours(final int seconds) {

		return String.format("%02d:%02d", seconds / 60, seconds % 60);

	}

	/**
	 * Returns the time in format <b> %02d:%02d:%02d if( minutes >60 )</b> or
	 * %02dsec if (seconds<60) %02d:%02d.
	 * 
	 * @param seconds the seconds
	 * @return the time edited in format <b> %02d:%02d:%02d if( minutes >60 )</b> or
	 *         %02d:%02d. [[SuppressWarningsSpartan]]
	 */
	public static String getTimeEdited(final int seconds) {
		if (seconds < 60) // duration < 1 minute
			return String.format("%02ds", seconds % 60);
		else if ((seconds / 60) / 60 <= 0) // duration < 1 hour
			return String.format("%02dm:%02d", (seconds / 60) % 60, seconds % 60);
		else
			return String.format("%02dh:%02dm:%02d", (seconds / 60) / 60, (seconds / 60) % 60, seconds % 60);
	}

	/**
	 * /** Returns the time in format <b> %02d:%02d:%02d if( minutes >60 )</b> or
	 * %02d:%02d.
	 *
	 * @param ms The milliseconds
	 * @return The Time edited in format <b> %02d:%02d:%02d if( minutes >60 )</b> or
	 *         %02d:%02d.
	 * 
	 */
	public static String millisecondsToTime(final long ms) {
		final int millis = (int) ((ms % 1000) / 100);
		// int seconds = (int) ((ms / 1000) % 60);
		// int minutes = (int) ((ms / (1000 * 60)) % 60);
		// int hours = (int) ((ms / (1000 * 60 * 60)) % 24);

		// if (minutes > 60)
		// return String.format("%02d:%02d:%02d.%d", hours, minutes, seconds, millis);
		// else
		// return String.format("%02d:%02d.%d", minutes, seconds, millis);

		return String.format(".%d", millis);

	}

	/**
	 * Returns the time of Audio to seconds
	 *
	 * @param name the name
	 * @param type <br>
	 *             1->URL <br>
	 *             2->FILE <br>
	 *             3->INPUTSTREAM
	 * @return time in milliseconds
	 */
	public static int durationInSeconds(final String name, final AudioType type) {

		final long time = TimeTool.durationInMilliseconds(name, type);

		return (int) ((time == 0 || time == -1) ? time : time / 1000);

		// Long microseconds = (Long)AudioSystem.getAudioFileFormat(new
		// File(audio)).properties().get("duration") int mili = (int)(microseconds /
		// 1000L);
		// int sec = milli / 1000 % 60;
		// int min = milli / 1000 / 60;

	}

	/**
	 * This method determines the duration of given data.
	 *
	 * @param input     The name of the input
	 * @param audioType URL, FILE, INPUTSTREAM, UNKOWN;
	 * @return Returns the duration of URL/FILE/INPUTSTREAM in milliseconds
	 */
	public static long durationInMilliseconds(final String input, final AudioType audioType) {
		return audioType == AudioType.FILE ? durationInMilliseconds_Part2(new File(input))
				: (audioType == AudioType.URL || audioType == AudioType.INPUTSTREAM || audioType == AudioType.UNKNOWN)
						? -1
						: -1;
	}

	/**
	 * Used by method durationInMilliseconds() to get file duration.
	 *
	 * @param file the file
	 * @return the int
	 */
	private static long durationInMilliseconds_Part2(final File file) {
		long milliseconds = -1;

		// exists?
		if (file.exists() && file.length() != 0) {

			// extension?
			final String extension = IOInfo.getFileExtension(file.getName());

			// MP3?
			if ("mp3".equals(extension)) {
				try {
					milliseconds = new MP3File(file).getMP3AudioHeader().getTrackLength() * 1000;

					// milliseconds = (int) ( (Long)
					// AudioSystem.getAudioFileFormat(file).properties().get("duration") / 1000 );

					// Get the result of mp3agic if the duration is bigger than 6 minutes
					// if (milliseconds / 1000 > 60 * 9) {
					// System.out.println("Entered..");
					// milliseconds = tryWithMp3Agic(file);
					// }

				} catch (final Exception ex) {
					System.err.println("Problem getting the time of-> " + file.getAbsolutePath());
				}
				// }
			}
			// OGG?
			else if("ogg".equals(extension)) {
				try {
					milliseconds = calculateOggVorbisAudioDuration(file);
				} catch (IOException ex) {
					System.err.println("Problem getting the time of-> " + file.getAbsolutePath());
				}
			}
			// WAV
			else if ("wav".equals(extension)) {
				try (AudioInputStream audioInputStream = AudioSystem.getAudioInputStream(file)) {
					final AudioFormat format = audioInputStream.getFormat();
					milliseconds = (int) (file.length() / (format.getFrameSize() * (int) format.getFrameRate())) * 1000;
				} catch (IOException | UnsupportedAudioFileException ex) {
					System.err.println("Problem getting the time of-> " + file.getAbsolutePath());
				}
			}
		}

		// System.out.println("Passed with error")
		return milliseconds < 0 ? -1 : milliseconds;
	}

	// Adapted from https://stackoverflow.com/a/44407355/8100469
	private static long calculateOggVorbisAudioDuration(File oggFile) throws IOException {
		int rate = -1;
		int length = -1;

		int size = (int) oggFile.length();
		byte[] t = new byte[size];

		try(FileInputStream stream = new FileInputStream(oggFile)) {
			stream.read(t);

			for (int i = size - 1 - 8 - 2 - 4; i >= 0 && length < 0; i--) { //4 bytes for "OggS", 2 unused bytes, 8 bytes for length
				// Looking for length (value after last "OggS")
				if (
						t[i] == (byte) 'O'
								&& t[i + 1] == (byte) 'g'
								&& t[i + 2] == (byte) 'g'
								&& t[i + 3] == (byte) 'S'
				) {
					byte[] byteArray = new byte[]{t[i + 6], t[i + 7], t[i + 8], t[i + 9], t[i + 10], t[i + 11], t[i + 12], t[i + 13]};
					ByteBuffer bb = ByteBuffer.wrap(byteArray);
					bb.order(ByteOrder.LITTLE_ENDIAN);
					length = bb.getInt(0);
				}
			}
			for (int i = 0; i < size - 8 - 2 - 4 && rate < 0; i++) {
				// Looking for rate (first value after "vorbis")
				if (
						t[i] == (byte) 'v'
								&& t[i + 1] == (byte) 'o'
								&& t[i + 2] == (byte) 'r'
								&& t[i + 3] == (byte) 'b'
								&& t[i + 4] == (byte) 'i'
								&& t[i + 5] == (byte) 's'
				) {
					byte[] byteArray = new byte[]{t[i + 11], t[i + 12], t[i + 13], t[i + 14]};
					ByteBuffer bb = ByteBuffer.wrap(byteArray);
					bb.order(ByteOrder.LITTLE_ENDIAN);
					rate = bb.getInt(0);
				}

			}
		}

		int duration = length / rate;
		return (duration*1000);
	}

}
