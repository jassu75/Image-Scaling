
import java.awt.*;
import java.awt.image.*;
import java.io.*;
import java.util.Arrays;

import javax.swing.*;

public class ImageDisplay {

	JFrame frame;
	JLabel lbIm1;
	BufferedImage imgOne;

	// Modify the height and width values here to read and display an image with
	// different dimensions.
	int width = 512;
	int height = 512;

	/**
	 * Read Image RGB
	 * Reads the image of given width and height at the given imgPath into the
	 * provided BufferedImage.
	 */
	private void readImageRGB(int width, int height, String imgPath, BufferedImage img) {
		try {
			int frameLength = width * height * 3;

			File file = new File(imgPath);
			RandomAccessFile raf = new RandomAccessFile(file, "r");
			raf.seek(0);

			long len = frameLength;
			byte[] bytes = new byte[(int) len];

			raf.read(bytes);

			int ind = 0;
			for (int y = 0; y < height; y++) {
				for (int x = 0; x < width; x++) {
					byte a = 0;
					byte r = bytes[ind];
					byte g = bytes[ind + height * width];
					byte b = bytes[ind + height * width * 2];

					int pix = 0xff000000 | ((r & 0xff) << 16) | ((g & 0xff) << 8) | (b & 0xff);
					// int pix = ((a << 24) + (r << 16) + (g << 8) + b);
					img.setRGB(x, y, pix);
					ind++;
				}
			}
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private int[] avgFilter(int x, int y, BufferedImage img) {
		int rsum = 0, gsum = 0, bsum = 0;
		int cnt = 0;
		int imgWidth = img.getWidth();
		int imgHeight = img.getHeight();

		for (int dx = -1; dx <= 1; dx++) {
			for (int dy = -1; dy <= 1; dy++) {
				int new_x = x + dx;
				int new_y = y + dy;

				if (new_x >= 0 && new_x < imgWidth && new_y >= 0 && new_y < imgHeight) {
					Color color = new Color(img.getRGB(new_x, new_y));
					rsum += color.getRed();
					gsum += color.getGreen();
					bsum += color.getBlue();
					cnt++;

				}

			}
		}

		int ravg = Math.round((float) rsum / cnt);
		int gavg = Math.round((float) gsum / cnt);
		int bavg = Math.round((float) bsum / cnt);

		return new int[] { ravg, gavg, bavg };

	}

	private BufferedImage uniformQuantization(BufferedImage img, int Q) {
		int imgWidth = img.getWidth();
		int imgHeight = img.getHeight();
		int bitsPerChannel = Q / 3;
		BufferedImage quantizedImage = new BufferedImage(imgWidth, imgHeight, BufferedImage.TYPE_INT_RGB);

		int numberOfLevels = (int) Math.pow(2, bitsPerChannel);
		int intervalSize = 256 / numberOfLevels;

		for (int x = 0; x < imgWidth; x++) {
			for (int y = 0; y < imgHeight; y++) {
				int pixel = img.getRGB(x, y);

				Color color = new Color(pixel);
				int[] rgb = new int[] { color.getRed(), color.getGreen(), color.getBlue() };
				int[] quantizedRgb = new int[3];

				for (int i = 0; i < 3; i++) {
					int quantizedLevel = rgb[i] / intervalSize;
					quantizedLevel = Math.min(quantizedLevel, numberOfLevels - 1);
					int representativeValue = (quantizedLevel * intervalSize) + (intervalSize / 2);
					quantizedRgb[i] = Math.min(255, Math.max(0, representativeValue));
				}

				Color quantizedColor = new Color(quantizedRgb[0], quantizedRgb[1], quantizedRgb[2]);
				int quantizedPixel = quantizedColor.getRGB();
				quantizedImage.setRGB(x, y, quantizedPixel);

			}
		}

		return quantizedImage;
	}

	public BufferedImage logarithmicQuantization(BufferedImage img, int Q, int M) {
		int imgWidth = img.getWidth();
		int imgHeight = img.getHeight();
		int bitsPerChannel = Q / 3;
		BufferedImage quantizedImage = new BufferedImage(imgWidth, imgHeight, BufferedImage.TYPE_INT_RGB);

		int numberOfLevels = (int) Math.pow(2, bitsPerChannel);

		int[] quantizedLevels = new int[numberOfLevels + 1];
		int[] representativeValues = new int[numberOfLevels];

		if (M == 0) {
			for (int l = 0; l <= numberOfLevels; l++) {
				double power = (double) l / numberOfLevels;
				quantizedLevels[l] = (int) Math.round(255 * Math.pow(power, 2));
			}
		} else if (M == 255) {
			for (int l = 0; l <= numberOfLevels; l++) {
				double power = (double) l / numberOfLevels;
				quantizedLevels[l] = (int) Math.round(255 * (1 - Math.pow(1 - power, 2)));
			}
		} else {
			int half = numberOfLevels / 2;
			for (int l = 1; l <= half; l++) {
				double power = Math.pow((double) l / half, 2);
				quantizedLevels[half - l] = (int) (M - Math.round(M * power));
				quantizedLevels[half + l] = (int) (M + Math.round((255 - M) * power));
			}
			quantizedLevels[0] = 0;
			quantizedLevels[half] = M;
			quantizedLevels[numberOfLevels] = 255;

		}

		for (int i = 0; i < numberOfLevels; i++) {
			representativeValues[i] = (quantizedLevels[i] + quantizedLevels[i + 1]) / 2;
		}

		for (int x = 0; x < imgWidth; x++) {
			for (int y = 0; y < imgHeight; y++) {
				int pixel = img.getRGB(x, y);

				Color color = new Color(pixel);
				int[] rgb = new int[] { color.getRed(), color.getGreen(), color.getBlue() };
				int[] quantizedRgb = new int[3];

				for (int i = 0; i < 3; i++) {
					int quantizedLevel = numberOfLevels - 1;
					for (int j = 0; j < numberOfLevels; j++) {
						if (rgb[i] >= quantizedLevels[j] && rgb[i] < quantizedLevels[j + 1]) {
							quantizedLevel = j;
							break;
						}
					}

					quantizedRgb[i] = Math.min(255, Math.max(0, representativeValues[quantizedLevel]));
				}

				Color quantizedColor = new Color(quantizedRgb[0], quantizedRgb[1], quantizedRgb[2]);
				int quantizedPixel = quantizedColor.getRGB();
				quantizedImage.setRGB(x, y, quantizedPixel);
			}
		}

		return quantizedImage;

	}

	private BufferedImage optimalIntervalQuantization(BufferedImage img, int Q) {
		int imgWidth = img.getWidth();
		int imgHeight = img.getHeight();
		int bitsPerChannel = Q / 3;
		BufferedImage quantizedImage = new BufferedImage(imgWidth, imgHeight, BufferedImage.TYPE_INT_RGB);

		int numberOfLevels = (int) Math.pow(2, bitsPerChannel);

		int[][] distribution = new int[3][256];
		int[][] quantizationMaps = new int[3][256];

		for (int x = 0; x < imgWidth; x++) {
			for (int y = 0; y < imgHeight; y++) {
				int pixel = img.getRGB(x, y);
				Color color = new Color(pixel);
				int[] rgb = new int[] { color.getRed(), color.getGreen(), color.getBlue() };
				for (int i = 0; i < 3; i++) {
					distribution[i][rgb[i]]++;
				}
			}
		}

		for (int i = 0; i < 3; i++) {
			int[] quantizedLevels = new int[numberOfLevels + 1];
			int[] representativeValues = new int[numberOfLevels];

			for (int l = 0; l <= numberOfLevels; l++) {
				quantizedLevels[l] = (l * 256) / numberOfLevels;
			}
			quantizedLevels[numberOfLevels] = 256;

			for (int iterations = 0; iterations <= 30; iterations++) {
				boolean flag = false;

				for (int l = 0; l < numberOfLevels; l++) {
					long sum = 0;
					long count = 0;

					for (int v = quantizedLevels[l]; v < quantizedLevels[l + 1]; v++) {
						sum += (long) v * distribution[i][v];
						count += (long) distribution[i][v];
					}

					if (count > 0) {
						representativeValues[l] = (int) Math.round((double) sum / count);
					} else {
						representativeValues[l] = (quantizedLevels[l] + quantizedLevels[l + 1]) / 2;
					}
				}

				for (int l = 1; l < numberOfLevels; l++) {
					int newQuantizedLevel = (representativeValues[l] + representativeValues[l - 1] + 1) / 2;
					if (newQuantizedLevel != quantizedLevels[l]) {
						quantizedLevels[l] = newQuantizedLevel;
						flag = true;
					}
				}

				if (!flag) {
					break;
				}
			}

			for (int v = 0; v < 256; v++) {
				for (int l = 0; l < numberOfLevels; l++) {
					if (v >= quantizedLevels[l] && v < quantizedLevels[l + 1]) {
						quantizationMaps[i][v] = representativeValues[l];
						break;
					}
				}
			}
		}

		for (int x = 0; x < imgWidth; x++) {
			for (int y = 0; y < imgHeight; y++) {
				int pixel = img.getRGB(x, y);
				Color color = new Color(pixel);
				int[] rgb = new int[] { color.getRed(), color.getGreen(), color.getBlue() };
				int[] quantizedRgb = new int[3];

				for (int i = 0; i < 3; i++) {
					quantizedRgb[i] = Math.min(255, Math.max(0, quantizationMaps[i][rgb[i]]));
				}
				Color quantizedColor = new Color(quantizedRgb[0], quantizedRgb[1], quantizedRgb[2]);
				int quantizedPixel = quantizedColor.getRGB();
				quantizedImage.setRGB(x, y, quantizedPixel);
			}
		}

		return quantizedImage;
	}

	private BufferedImage resampleImage(BufferedImage img, float scale) {
		if (scale == 1.0f) {
			return img;
		}

		int imgWidth = img.getWidth();
		int imgHeight = img.getHeight();
		int resampledImageWidth = Math.round(imgWidth * scale);
		int resampledImageHeight = Math.round(imgHeight * scale);

		BufferedImage resampledImage = new BufferedImage(
				resampledImageWidth, resampledImageHeight, BufferedImage.TYPE_INT_RGB);

		for (int x = 0; x < resampledImageWidth; x++) {
			for (int y = 0; y < resampledImageHeight; y++) {
				int scaled_x = Math.round(x / scale);
				int scaled_y = Math.round(y / scale);

				int clamped_x = Math.max(0, Math.min(scaled_x, imgWidth - 1));
				int clamped_y = Math.max(0, Math.min(scaled_y, imgHeight - 1));

				int[] rgb = avgFilter(clamped_x, clamped_y, img);
				Color color = new Color(rgb[0], rgb[1], rgb[2]);
				int pixel = color.getRGB();
				resampledImage.setRGB(x, y, pixel);

			}
		}

		return resampledImage;

	}

	private BufferedImage quantizeImage(BufferedImage img, int Q, int M) {

		BufferedImage quantizedImage;

		if (M == -1) {
			quantizedImage = uniformQuantization(img, Q);
		} else if (M == 256) {
			quantizedImage = optimalIntervalQuantization(img, Q);
		} else {
			quantizedImage = logarithmicQuantization(img, Q, M);
		}

		return quantizedImage;

	}

	private void errorAnalysis(BufferedImage originalImage, BufferedImage quantizedImage) {
		double meanSquareError = meanSquareError(originalImage, quantizedImage);
		double meanAbsoluteError = meanAbsoluteError(originalImage, quantizedImage);

		System.out.println("MSE: " + meanSquareError);
		System.out.println("MAE: " + meanAbsoluteError);
	}

	private double meanSquareError(BufferedImage originalImage, BufferedImage quantizedImage) {
		double error = 0;
		int count = 0;

		int imgWidth = originalImage.getWidth();
		int imgHeight = originalImage.getHeight();

		for (int x = 0; x < imgWidth; x++) {
			for (int y = 0; y < imgHeight; y++) {

				int originalPixel = originalImage.getRGB(x, y);
				Color originalColor = new Color(originalPixel);
				int[] originalRgb = new int[] { originalColor.getRed(), originalColor.getGreen(),
						originalColor.getBlue() };

				int quantizedPixel = quantizedImage.getRGB(x, y);
				Color quantizedColor = new Color(quantizedPixel);
				int[] quantizedRgb = new int[] { quantizedColor.getRed(), quantizedColor.getGreen(),
						quantizedColor.getBlue() };

				for (int i = 0; i < 3; i++) {
					error += Math.pow(originalRgb[i] - quantizedRgb[i], 2);
					count++;
				}
			}
		}

		return error / count;
	}

	private double meanAbsoluteError(BufferedImage originalImage, BufferedImage quantizedImage) {
		double error = 0;
		int count = 0;

		int imgWidth = originalImage.getWidth();
		int imgHeight = originalImage.getHeight();

		for (int x = 0; x < imgWidth; x++) {
			for (int y = 0; y < imgHeight; y++) {

				int originalPixel = originalImage.getRGB(x, y);
				Color originalColor = new Color(originalPixel);
				int[] originalRgb = new int[] { originalColor.getRed(), originalColor.getGreen(),
						originalColor.getBlue() };

				int quantizedPixel = quantizedImage.getRGB(x, y);
				Color quantizedColor = new Color(quantizedPixel);
				int[] quantizedRgb = new int[] { quantizedColor.getRed(), quantizedColor.getGreen(),
						quantizedColor.getBlue() };

				for (int i = 0; i < 3; i++) {
					error += Math.abs(originalRgb[i] - quantizedRgb[i]);
					count++;
				}
			}
		}

		return error / count;
	}

	private void showIms(String[] args) {
		if (args.length != 4) {
			System.out.println("Incorrect parameters. Require Path, Scale, Q, M. ");
			return;
		}
		String path = args[0];
		float scale = Float.parseFloat(args[1]);
		int Q = Integer.parseInt(args[2]);
		int M = Integer.parseInt(args[3]);

		// Read in the specified image
		imgOne = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
		readImageRGB(width, height, path, imgOne);
		BufferedImage resampledImage = resampleImage(imgOne, scale);
		BufferedImage quantizedImage = quantizeImage(resampledImage, Q, M);

		if (scale == 1.0f) {
			errorAnalysis(imgOne, quantizedImage);
		}

		// Use label to display the image
		frame = new JFrame();
		GridBagLayout gLayout = new GridBagLayout();
		frame.getContentPane().setLayout(gLayout);

		JLabel lbOriginal = new JLabel(new ImageIcon(imgOne));
		JLabel lbQuantized = new JLabel(new ImageIcon(quantizedImage));

		GridBagConstraints c = new GridBagConstraints();
		c.fill = GridBagConstraints.HORIZONTAL;
		c.anchor = GridBagConstraints.CENTER;
		c.weightx = 0.5;

		c.gridx = 0;
		c.gridy = 0;
		frame.getContentPane().add(lbOriginal, c);

		c.gridx = 1;
		c.gridy = 0;
		frame.getContentPane().add(lbQuantized, c);

		frame.pack();
		frame.setVisible(true);
	}

	public static void main(String[] args) {
		ImageDisplay ren = new ImageDisplay();
		ren.showIms(args);
	}

}
