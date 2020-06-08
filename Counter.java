import java.awt.image.*;
import java.io.*;
import java.util.*;

import java.awt.GridLayout;
import java.awt.geom.AffineTransform;

import javax.imageio.*;
import javax.swing.*;

public class Counter extends JFrame
{
	static String fileName;
	BufferedImage image;
	BufferedImage finalOutput;
	List<BufferedImage> outputs = new ArrayList<BufferedImage>();
	int count;
	int area;
	boolean[][] imageMap;

	public static void main(String[] args)
	{
		try
		{
			if (args.length != 1) throw new IllegalArgumentException("Usage: java Counter <image file name>");
			else fileName = args[0]; new Counter();
		}
		catch (Exception e) { System.out.println(e); }
	}

	public Counter() throws IOException, InterruptedException
	{
		super("");
		setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
		process();
		GridLayout grid = new GridLayout(3, 3);
		this.setLayout(grid);
		for (BufferedImage image : outputs)
			this.add(new JLabel(new ImageIcon(new AffineTransformOp(AffineTransform.getScaleInstance(0.5, 0.5), AffineTransformOp.TYPE_BILINEAR).filter(image, null))));
		this.setTitle("Number of cells counted: " + count);
		pack();
		setVisible(true);
	}

	public void process() throws IOException
	{
		outputs.add(ImageIO.read(new File(fileName)));

		outputs.add(laplDiff(outputs.get(outputs.size() - 1)));
		outputs.add(contrast(outputs.get(outputs.size() - 1), 10, 150));
		outputs.add(medianFilter(outputs.get(outputs.size() - 1)));
		outputs.add(gaussBlur(outputs.get(outputs.size() - 1)));
		outputs.add(contrast(outputs.get(outputs.size() - 1), 30, 150));
		outputs.add(gaussBlur(outputs.get(outputs.size() - 1)));
		outputs.add(threshold(outputs.get(outputs.size() - 1), 200));
		outputs.add(open(outputs.get(outputs.size() - 1), 1));
		outputs.add(close(outputs.get(outputs.size() - 1), 1));
		outputs.add(open(outputs.get(outputs.size() - 1), 2));

		finalOutput = cloneBI(outputs.get(outputs.size() - 1));

		imageMap = new boolean[finalOutput.getWidth()][finalOutput.getHeight()];
		for (int x = 0; x < finalOutput.getWidth(); x++)
			for (int y = 0; y < finalOutput.getHeight(); y++)
				imageMap[x][y] = (finalOutput.getRGB(x, y) & 1) == 1;

		for (int x = 0; x < imageMap.length; x++)
			for (int y = 0; y < imageMap[0].length; y++)
			{
				countFromPixel(x, y);
				if (area > 9) count++;
				area = 0;
			}

	}

	/**
	 * Increases the contrast of an image by expanding the dynamic range within the given parameters.
	 * @param image The image object to be processed. The object is unchanged.
	 * @param low Lower threshold to be reset to 0
	 * @param high Upper threshold to be reset to 255
	 * @return An image object with the effect applied
	 */
	public static BufferedImage contrast(BufferedImage image, int low, int high)
	{
		BufferedImage temp = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_BYTE_GRAY);
		for (int x = 0; x < image.getWidth(); x++)
			for (int y = 0; y < image.getHeight(); y++)
				temp.setRGB(x, y, contrast(image.getRGB(x, y) & 0xFFFFFF, low, high) + 0xFF000000);
		return temp;
	}

	/**
	 * Takes a greyscale pixel value in 24-bits, and transforms it to its relative position between the low and high parameteric values.
	 * @param rgb 24-bit length RGB value to transform
	 * @param low Lower threshold to be reset to 0
	 * @param high Upper threshold to be reset to 255
	 * @return The transformed integer value
	 */
	public static int contrast(int rgb, int low, int high)
	{
		int mask = 0x10101;
		rgb /= mask;
		float diff = high - (float)low;
		if (rgb < low) return 0;
		else if (rgb > high) return 255 * mask;
		return (int)((rgb - low) / diff * 255) * mask;
	}

	/**
	 * Applies a Gaussian matrix to an image.
	 * @param image The image object to be processed. The object is unchanged.
	 * @return An image object with the effect applied
	 */
	public static BufferedImage gaussBlur(BufferedImage image)
	{
		BufferedImage temp = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_BYTE_GRAY);
		float[] matrix = new float[]
		{
			0.075f, 0.125f, 0.075f,
			0.125f, 0.200f, 0.125f,
			0.075f, 0.125f, 0.075f
		};
		ConvolveOp blurOp = new ConvolveOp(new Kernel(3, 3, matrix), ConvolveOp.EDGE_NO_OP, null);
		temp = blurOp.filter(image, null);
		return temp;
	}

	/**
	 * Applies a Laplacian matrix to an image.
	 * @param image The image object to be processed. The object is unchanged.
	 * @return An image object with the effect applied
	 */
	public static BufferedImage laplDiff(BufferedImage image)
	{
		BufferedImage temp = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_BYTE_GRAY);
		float[] matrix = new float[]
		{
			0.0f, 0.0f, -1.0f, 0.0f, 0.0f,
			0.0f, -1.0f, -1.0f, -1.0f, 0.0f,
			-1.0f, -2.0f, 17.0f, -2.0f, -1.0f,
			0.0f, -1.0f, -2.0f, -1.0f, 0.0f,
			0.0f, 0.0f, -1.0f, 0.0f, 0.0f,
		};
		ConvolveOp blurOp = new ConvolveOp(new Kernel(5, 5, matrix), ConvolveOp.EDGE_NO_OP, null);
		temp = blurOp.filter(image, null);
		return temp;
	}

	/**
	 * Resets each pixel in an image to the median value of its neighbourhood.
	 * @param image The image object to be processed. The object is unchanged.
	 * @return An image object with the effect applied
	 */
	public static BufferedImage medianFilter(BufferedImage image)
	{
		BufferedImage temp = cloneBI(image);
		for (int x = 1; x < image.getWidth() - 1; x++)
			for (int y = 1; y < image.getHeight() - 1; y++)
			{
				List<Integer> matrix = new ArrayList<Integer>();
				for (int a = x - 1; a <= x + 1; a++)
					for (int b = y - 1; b <= y + 1; b++)
						matrix.add(image.getRGB(x, y));
				matrix.sort((Integer i1, Integer i2) -> i1.intValue() - i2.intValue());
				image.setRGB(x, y, matrix.get(4).intValue());
			}
		return temp;
	}

	/**
	 * Erodes the image a number of times, then dilates the image the same number of times.
	 * @param image The image object to be processed. The object is unchanged.
	 * @param depth The iterations of sub-steps
	 * @return An image object with the effect applied
	 */
	public static BufferedImage open(BufferedImage image, int depth)
	{
		BufferedImage temp = cloneBI(image);
		for (int i = 0; i < depth; i++) temp = erode(temp);
		for (int i = 0; i < depth; i++) temp = dilate(temp);
		return temp;
	}

	/**
	 * Dilates the image a number of times, then erodes the image the same number of times.
	 * @param image The image object to be processed. The object is unchanged.
	 * @param depth The iterations of sub-steps
	 * @return An image object with the effect applied
	 */
	public static BufferedImage close(BufferedImage image, int depth)
	{
		BufferedImage temp = cloneBI(image);
		for (int i = 0; i < depth; i++) temp = dilate(temp);
		for (int i = 0; i < depth; i++) temp = erode(temp);
		return temp;
	}

	/**
	 * Deactivates active pixels if they neighbour an inactive pixel.
	 * @param image The image object to be processed. The object is unchanged.
	 * @return An image object with the effect applied
	 */
	public static BufferedImage erode(BufferedImage image)
	{ return invert(dilate(invert(image))); }

	/**
	 * Activates inactive pixels if they neighbour an activated pixel.
	 * @param image The image object to be processed. The object is unchanged.
	 * @return An image object with the effect applied
	 */
	public static BufferedImage dilate(BufferedImage image)
	{
		BufferedImage temp = cloneBI(image);
		float[] matrix = new float[]
		{
			0.0f, 1.0f, 0.0f, 
			1.0f, 1.0f, 1.0f, 
			0.0f, 1.0f, 0.0f
		};
		ConvolveOp growOp = new ConvolveOp(new Kernel(3, 3, matrix), ConvolveOp.EDGE_NO_OP, null);
		temp = growOp.filter(image, null);
		return temp;
	}

	/**
	 * Inverts pixel activation - black turns white, white turns black.
	 * @param image The image object to be processed. The object is unchanged.
	 * @return An image object with the effect applied
	 */
	public static BufferedImage invert(BufferedImage image)
	{
		BufferedImage temp = cloneBI(image);
		for (int x = 0; x < temp.getWidth(); x++)
			for (int y = 0; y < temp.getHeight(); y++)
				temp.setRGB(x, y, (temp.getRGB(x, y) == 0xFF000000) ? 0xFFFFFFFF : 0xFF000000);
		return temp;
	}

	/**
	 * Creates an image of binary pixels that are either 0 or 1 (black or white).
	 * @param image The image object to be processed. The object is unchanged.
	 * @param cutoff Integer cutoff. Pixels with value above the cutoff will be active, and below will be inactive.
	 * @return An image object with the effect applied
	 */
	public static BufferedImage threshold(BufferedImage image, int cutoff)
	{
		BufferedImage temp = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_BYTE_BINARY);
		for (int x = 0; x < image.getWidth(); x++)
			for (int y = 0; y < image.getHeight(); y++)
				temp.setRGB(x, y, (image.getRGB(x, y) & 0xFFFFFF) >= (cutoff * 0x10101) ? 0xFFFFFFFF : 0);
		image = temp;
		return temp;
	}

	/**
	 * Returns an identical duplicate BufferedImage object.
	 * @param image The image object to be processed. The object is unchanged.
	 * @return Duplicate object of separate reference
	 */
	public static BufferedImage cloneBI(BufferedImage image)
	{ return new ConvolveOp(new Kernel(1, 1, new float[] {1f})).filter(image, null); }

	/**
	 * 
	 * @param x 
	 * @param y
	 */
	public void countFromPixel(int x, int y)
	{
		if (!imageMap[x][y]) return;
		imageMap[x][y] = false;
		area++;
		try { if (x > 0) countFromPixel(x - 1, y); } catch (Exception e) {}
		try { if (y > 0) countFromPixel(x, y - 1); } catch (Exception e) {}
		try { if (x < imageMap.length - 1) countFromPixel(x + 1, y); } catch (Exception e) {}
		try { if (y < imageMap[0].length - 1) countFromPixel(x, y + 1); } catch (Exception e) {}
	}
}