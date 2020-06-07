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
		outputs.add(threshold(outputs.get(outputs.size() - 1)));
		outputs.add(open(outputs.get(outputs.size() - 1), 1));
		outputs.add(close(outputs.get(outputs.size() - 1), 1));
		outputs.add(open(outputs.get(outputs.size() - 1), 2));
		outputs.add(dilate(outputs.get(outputs.size() - 1)));

		finalOutput = cloneBI(outputs.get(outputs.size() - 1));
		for (int x = 0; x < finalOutput.getWidth(); x++)
			for (int y = 0; y < finalOutput.getHeight(); y++)
				countFromPixel(x, y);
				if (area > 9) count++;
				area = 0;
	}

	public static BufferedImage contrast(BufferedImage image, int low, int high)
	{
		BufferedImage temp = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_BYTE_GRAY);
		for (int x = 0; x < image.getWidth(); x++)
			for (int y = 0; y < image.getHeight(); y++)
				temp.setRGB(x, y, contrast(image.getRGB(x, y) & 0xFFFFFF, low, high) + 0xFF000000);
		return temp;
	}

	public static int contrast(int rgb, int low, int high)
	{
		int mask = 0x10101;
		rgb /= mask;
		float diff = high - (float)low;
		if (rgb < low) return 0;
		else if (rgb > high) return 255 * mask;
		return (int)((rgb - low) / diff * 255) * mask;
	}

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
				Collections.sort(matrix);
				image.setRGB(x, y, matrix.get(4).intValue());
			}
		return temp;
	}

	public static BufferedImage open(BufferedImage image, int depth)
	{
		BufferedImage temp = cloneBI(image);
		for (int i = 0; i < depth; i++) temp = erode(temp);
		for (int i = 0; i < depth; i++) temp = dilate(temp);
		return temp;
	}

	public static BufferedImage close(BufferedImage image, int depth)
	{
		BufferedImage temp = cloneBI(image);
		for (int i = 0; i < depth; i++) temp = dilate(temp);
		for (int i = 0; i < depth; i++) temp = erode(temp);
		return temp;
	}

	public static BufferedImage erode(BufferedImage image)
	{ return invert(dilate(invert(image))); }

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

	public static BufferedImage invert(BufferedImage image)
	{
		BufferedImage temp = cloneBI(image);
		for (int x = 0; x < temp.getWidth(); x++)
			for (int y = 0; y < temp.getHeight(); y++)
				temp.setRGB(x, y, (temp.getRGB(x, y) == 0xFF000000) ? 0xFFFFFFFF : 0xFF000000);
		return temp;
	}

	public static BufferedImage threshold(BufferedImage image)
	{
		BufferedImage temp = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_BYTE_BINARY);
		for (int x = 0; x < image.getWidth(); x++)
			for (int y = 0; y < image.getHeight(); y++)
				temp.setRGB(x, y, (image.getRGB(x, y) & 0xFFFFFF) >= (0xFFFFFF) ? 0xFFFFFFFF : 0);
		image = temp;
		return temp;
	}

	public static BufferedImage cloneBI(BufferedImage image)
	{ return new ConvolveOp(new Kernel(1, 1, new float[] {1f})).filter(image, null); }

	public void countFromPixel(int x, int y)
	{
		try{
			if (finalOutput.getRGB(x, y) != 0xFFFFFFFF) return;
			finalOutput.setRGB(x, y, 0xFF000000);
			area++;
			try { if (x > 0) countFromPixel(x - 1, y); } catch (Exception e) {}
			try { if (y > 0) countFromPixel(x, y - 1); } catch (Exception e) {}
			try { if (x < finalOutput.getWidth() - 1) countFromPixel(x + 1, y); } catch (Exception e) {}
			try { if (y < finalOutput.getHeight() - 1) countFromPixel(x, y + 1); } catch (Exception e) {}

		}catch (Exception e)
		{}
	}
}