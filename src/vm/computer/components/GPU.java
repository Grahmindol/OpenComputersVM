package vm.computer.components;

import javafx.scene.image.PixelFormat;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;
import javafx.scene.image.WritablePixelFormat;
import li.cil.repack.com.naef.jnlua.LuaRuntimeException;
import org.json.JSONObject;
import vm.computer.Glyph;
import vm.computer.LuaUtils;
import vm.computer.Machine;
import vm.computer.components.base.ComponentBase;

import java.nio.IntBuffer;

public class Gpu extends ComponentBase {
	public static class Color {
		static final Color
			BLACK = new Color(0x000000),
			WHITE = new Color(0xFFFFFF),
			BLUE = new Color(0x0000FF);
		
		int value;
		boolean isPaletteIndex;

		Color(int value) {
			this(value, false);
		}

		Color(int value, boolean isPaletteIndex) {
			this.value = 0xFF000000 | value;
			this.isPaletteIndex = isPaletteIndex;
		}
	}
	
	private class Pixel {
		Color background, foreground;
		int code;

		Pixel(Color background, Color foreground, int code) {
			this.background = background;
			this.foreground = foreground;
			this.code = code;
		}
		
		Pixel(Pixel source) {
			this.background = source.background;
			this.foreground = source.foreground;
			this.code = source.code;
		}
	}

	// Le thread de mise à jour, qui rafraîchit les informations sur les pixels sur demande.
	public class UpdaterThread extends Thread {
		// Essentiellement, un limiteur de FPS.
		public int waitDelay = 16;
		public int[] buffer;
		
		private WritablePixelFormat<IntBuffer> argbPreInstance;
		private boolean needUpdate = false;

		public UpdaterThread() {
			super("GPU-updater");
			argbPreInstance = PixelFormat.getIntArgbPreInstance();
		}

		public void setBufferSize(int size) {
			synchronized (this) {
				buffer = new int[size];
			}
		}
		
		public void update() {
			update(0, 0, width - 1, height - 1);
		}

		public void update(int fromX, int fromY, int toX, int toY) {
			synchronized (this) {
				int bufferIndex = fromY * (Glyph.WIDTH * Glyph.HEIGHT * width) + fromX * (Glyph.WIDTH),
					lineStep = GlyphWidthMulWidthMulGlyphHeight - (toX - fromX + 1) * Glyph.WIDTH,
					glyphIndex,
					background,
					foreground;

				for (int y = fromY; y <= toY; y++) {
					for (int x = fromX; x <= toX; x++) {
						background = pixels[y][x].background.isPaletteIndex ? palette[pixels[y][x].background.value] : pixels[y][x].background.value;
						foreground = pixels[y][x].foreground.isPaletteIndex ? palette[pixels[y][x].foreground.value] : pixels[y][x].foreground.value;
						glyphIndex = 0;
						
						for (int j = 0; j < Glyph.HEIGHT; j++) {
							for (int i = 0; i < Glyph.WIDTH; i++) {
								buffer[bufferIndex + i] = Glyph.map[pixels[y][x].code].microPixels[glyphIndex] ? foreground : background;

								glyphIndex++;
							}

							bufferIndex += GlyphWIDTHMulWidth;
						}

						bufferIndex += Glyph.WIDTH - GlyphWidthMulWidthMulGlyphHeight;
					}

					bufferIndex += lineStep;
//					bufferIndex += (width - toX) * Glyph.WIDTH * Glyph.HEIGHT + fromX * Glyph.WIDTH;
//					bufferIndex += GlyphWidthMulWidthMulGlyphHeight - GlyphWIDTHMulWidth;
				}

				needUpdate = true;
			}
		}
		
		public void setPixels() {
			pixelWriter.setPixels(
				0,
				0,
				GlyphWIDTHMulWidth,
				GlyphHEIGHTMulHeight,
				argbPreInstance,
				buffer,
				0,
				GlyphWIDTHMulWidth
			);

			needUpdate = false;
		}

		@Override
		public void run() {
			synchronized (this) {
				while (true) {
					try {
						wait(waitDelay);

						if (needUpdate)
							setPixels();
					}
					catch (InterruptedException | ThreadDeath e) {
						break;
					}
				}
			}
		}
	}

	public int
		width,
		height,
		GlyphWIDTHMulWidth,
		GlyphHEIGHTMulHeight,
		GlyphWidthMulWidthMulGlyphHeight;
	public UpdaterThread updaterThread = new UpdaterThread();

	private Color background = Color.BLACK, foreground = Color.WHITE;
	private int[] palette = new int[16];
	private Pixel[][] pixels;
	private PixelWriter pixelWriter;

	private String BindedScreenAddr = "";

	public Gpu(Machine machine, String address, JSONObject obj) {
		super(machine, address,obj);
		
		updaterThread.start();
		this.rawSetResolution(obj.optInt("width",80), obj.optInt("height",25));
	}

	@Override
	public void pushProxyFields() {
		super.pushProxyFields();

		machine.lua.pushJavaFunction(args -> {
			int
				x = args.checkInteger(1) - 1,
				y = args.checkInteger(2) - 1;

			checkCoordinatesAndThrow(x, y);
			
			// Пушим символ, цвет текста и цвет фона
			machine.lua.pushString(new String(Character.toChars(pixels[y][x].code)));
			machine.lua.pushInteger(pixels[y][x].foreground.isPaletteIndex ? palette[pixels[y][x].foreground.value] : pixels[y][x].foreground.value);
			machine.lua.pushInteger(pixels[y][x].background.isPaletteIndex ? palette[pixels[y][x].background.value] :
				pixels[y][x].background.value);
			
			// Если цвета из палитры, то пушим еще и индексы палитры, либо нил для каждого из цветов
			if (pixels[y][x].foreground.isPaletteIndex)
				machine.lua.pushInteger(pixels[y][x].foreground.value);
			else
				machine.lua.pushNil();
			
			if (pixels[y][x].background.isPaletteIndex)
				machine.lua.pushInteger(pixels[y][x].background.value);
			else
				machine.lua.pushNil();
			
			return 5;
		});
		machine.lua.setField(-2, "get");
		
		machine.lua.pushJavaFunction(args -> {
			int x = args.checkInteger(1) - 1,
				y = args.checkInteger(2) - 1;

			int w = Math.max(0, args.checkInteger(3)),
				h = Math.max(0, args.checkInteger(4));

			int dstX = x + args.checkInteger(5),
				dstY = y + args.checkInteger(6);
			
			// Копипиздим
			Pixel[][] copy = new Pixel[h][w];
			for (int j = 0; j < h; j++)
				for (int i = 0; i < w; i++)
					if (checkCoordinates(x + i, y + j))
						copy[j][i] = new Pixel(pixels[y + j][x + i]);
					
			// Вставляем заточку в почку
			int pasteX = dstX,
				pasteY = dstY;
			for (int j = 0; j < h; j++) {
				for (int i = 0; i < w; i++) {
					if (checkCoordinates(pasteX, pasteY))
						if (copy[j][i] != null)
							pixels[pasteY][pasteX] = copy[j][i];

					pasteX++;
				}

				pasteX = dstX;
				pasteY++;
			}
			
			updaterThread.update(
				fixCoord(dstX, width),
				fixCoord(dstY, height),
				fixCoord(dstX + w - 1, width),
				fixCoord(dstY + h - 1, height)
			);
			
			machine.lua.pushBoolean(true);
			return 1;
		});
		machine.lua.setField(-2, "copy");

		machine.lua.pushJavaFunction(args -> {
			machine.lua.pushInteger(160);
			machine.lua.pushInteger(50);

			return 2;
		});
		machine.lua.setField(-2, "maxResolution");

		machine.lua.pushJavaFunction(args -> {
			rawSetResolution(args.checkInteger(1), args.checkInteger(2));

			return 0;
		});
		machine.lua.setField(-2, "setResolution");

		machine.lua.pushJavaFunction(args -> {
			int x = args.checkInteger(1) - 1,
				y = args.checkInteger(2) - 1;
			String text = args.checkString(3);
			
			int	setX = x;
			for (int i = 0; i < text.length(); i++) {
				if (checkCoordinates(setX, y))
					rawSet(setX, y, text.codePointAt(i));
				
				setX++;
			}
			
			// Пабыстрее)00
			y = fixCoord(y, height);
			updaterThread.update(
				fixCoord(x, width),
				y,
				fixCoord(x + text.length() - 1, width),
				y
			);

			return 0;
		});
		machine.lua.setField(-2, "set");

		machine.lua.pushJavaFunction(args -> {
			int x = fixCoord(args.checkInteger(1) - 1, width),
				y = fixCoord(args.checkInteger(2) - 1, height);
			
			rawFill(
				x,
				y,
				fixCoord(x + args.checkInteger(3) - 1, width),
				fixCoord(y + args.checkInteger(4) - 1, height),
				args.checkString(5).codePointAt(0)
			);

			return 0;
		});
		machine.lua.setField(-2, "fill");

		machine.lua.pushJavaFunction(args -> {
			Color oldColor = background;
			background = new Color(args.checkInteger(1), !args.isNoneOrNil(2) && args.checkBoolean(2));
            
			return setColorPush(oldColor);
		});
		machine.lua.setField(-2, "setBackground");

		machine.lua.pushJavaFunction(args -> {
			Color oldColor = foreground;
			foreground = new Color(args.checkInteger(1), !args.isNoneOrNil(2) && args.checkBoolean(2));

			return setColorPush(oldColor);
		});
		machine.lua.setField(-2, "setForeground");

		machine.lua.pushJavaFunction(args -> getColorPush(background));
		machine.lua.setField(-2, "getBackground");

		machine.lua.pushJavaFunction(args -> getColorPush(foreground));
		machine.lua.setField(-2, "getForeground");

		machine.lua.pushJavaFunction(args -> {
			machine.lua.pushInteger(width);
			machine.lua.pushInteger(height);

			return 2;
		});
		machine.lua.setField(-2, "getResolution");

		machine.lua.pushJavaFunction(args -> {
			machine.lua.pushInteger(width);
			machine.lua.pushInteger(height);

			return 2;
		});
		machine.lua.setField(-2, "getViewport");

		machine.lua.pushJavaFunction(args -> {
			machine.lua.pushString(BindedScreenAddr);
			return 1;
		});
		machine.lua.setField(-2, "getScreen");

		machine.lua.pushJavaFunction(args -> {
			machine.lua.pushInteger(8);
			return 1;
		});
		machine.lua.setField(-2, "getDepth");

		machine.lua.pushJavaFunction(args -> {
			int index = args.checkInteger(1);
			int value = args.checkInteger(2);
			checkPaletteIndex(index);
			
			int oldValue = palette[index];
			palette[index] = value;
			machine.lua.pushInteger(oldValue);
			
			return 1;
		});
		machine.lua.setField(-2, "setPaletteColor");

		machine.lua.pushJavaFunction(args -> {
			int index = args.checkInteger(1);
			checkPaletteIndex(index);
			
			machine.lua.pushInteger(palette[index]);

			return 1;
		});
		machine.lua.setField(-2, "getPaletteColor");

		machine.lua.pushJavaFunction(args -> {
			BindedScreenAddr = args.checkString(1);

			machine.lua.pushBoolean(true);

			return 1;
		});
		machine.lua.setField(-2, "bind");

		LuaUtils.pushBooleanFunction(machine.lua, "setViewport", true);
		LuaUtils.pushBooleanFunction(machine.lua, "setDepth", true);


	}

	@Override
	public JSONObject toJSONObject() {
		return super.toJSONObject().put("width", width).put("height", height);
	}

	// Retour d'informations sur la couleur actuelle.
    private int getColorPush(Color color) {
        if (color.isPaletteIndex) {
            machine.lua.pushInteger(color.value);
            machine.lua.pushBoolean(true);
            return 2;
        }
        
        machine.lua.pushInteger(color.value);
        return 1;
    }
	
    // Retour d'informations sur l'ancienne couleur avant l'opération gpu.set.
	private int setColorPush(Color color) {
		if (color.isPaletteIndex) {
			machine.lua.pushInteger(palette[color.value]);
			machine.lua.pushInteger(color.value);
			return 2;
		}
		
        machine.lua.pushInteger(color.value);
        return 1;
	}
	
	private int checkPaletteIndex(int index) {
		if (index < 0 || index > 15)
			throw new LuaRuntimeException("palette index is out of range [0; 15]");
		
		return index;
	}
	
	private int fixCoord(int c, int limiter) {
		return Math.max(0, Math.min(c, limiter - 1));
	}
	
	public void rawSetResolution(int newWidth, int newHeight) {
		width = newWidth;
		height = newHeight;
		GlyphWIDTHMulWidth = width * Glyph.WIDTH;
		GlyphHEIGHTMulHeight = height * Glyph.HEIGHT;
		GlyphWidthMulWidthMulGlyphHeight = GlyphWIDTHMulWidth * Glyph.HEIGHT;
		
		Pixel[][] oldPixels = pixels;
		pixels = new Pixel[height][width];
		
		// If we just created the graphics object and there are no pixels yet in memory.
		if (oldPixels == null)
			flushPixels();
		// Well, otherwise we copy the old pixels into the new buffer.
		else {
			for (int j = 0; j < height; j++)
				for (int i = 0; i < width; i++)
					if (j < oldPixels.length && i < oldPixels[0].length)
						pixels[j][i] = oldPixels[j][i];
					else
						pixels[j][i] = new Pixel(background, foreground, 32);
		}
		
		// First, we create a writable picture.
		WritableImage writableImage = new WritableImage(GlyphWIDTHMulWidth, GlyphHEIGHTMulHeight);
		pixelWriter = writableImage.getPixelWriter();
		
		// Then we update the graphics buffer into it.
		updaterThread.setBufferSize(GlyphWIDTHMulWidth * GlyphHEIGHTMulHeight);
		updaterThread.update();
		updaterThread.setPixels();

		// And then we can insert it into the widget.
		try {
			
			((Screen)machine.listComponents.get("screen").get(BindedScreenAddr)).controller.screenImageView.setImage(writableImage);
			((Screen)machine.listComponents.get("screen").get(BindedScreenAddr)).controller.setGlyphMul(GlyphWIDTHMulWidth,GlyphHEIGHTMulHeight);
			// And check the dimensions.
			((Screen)machine.listComponents.get("screen").get(BindedScreenAddr)).controller.checkImageViewBindings();
		}catch (NullPointerException ignored){

		}


	}

	private void flushPixels() {
		for (int y = 0; y < height; y++)
			for (int x = 0; x < width; x++)
				pixels[y][x] = new Pixel(background, foreground, 32);
	}
	
	public void flush() {
		background = Color.BLACK;
		foreground = Color.WHITE;

		flushPixels();
			
		for (int i = 0; i < palette.length; i++)
			palette[i] = 0xFF000000;
	}

	private boolean checkCoordinates(int x, int y) {
		return x >= 0 && x < width && y >= 0 && y < height;
	}
	
	private void checkCoordinatesAndThrow(int x, int y) {
		if (!checkCoordinates(x, y))
			throw new LuaRuntimeException("screen coordinate is out of range [" + width + "; " + height + "]: " + x + 
				", " + y);
	}
	
	public void rawSet(int x, int y, int code) {
		pixels[y][x].background = background;
		pixels[y][x].foreground = foreground;
		pixels[y][x].code = code;
	}

	public void rawText(int x, int y, String text) {
		for (int i = 0; i < text.length(); i++) {
			if (checkCoordinates(x, y))
				rawSet(x, y, text.codePointAt(i));
			
			x++;
		}
	}

	public void rawFill(int fromX, int fromY, int toX, int toY, int s) {
		for (int j = fromY; j <= toY; j++)
			for (int i = fromX; i <= toX; i++)
				rawSet(i, j, s);
	
		updaterThread.update(fromX, fromY, toX, toY);
	}

	public void rawError(String text) {
		System.out.println(text);

		String[] lines = text.split("\n");

		background = Color.BLUE;
		foreground = Color.WHITE;

		rawFill(0, 0, width - 1, height - 1, 32);

		int y = height / 2 - lines.length / 2;
		for (int i = 0; i < lines.length; i++) {
			rawText(width / 2 - lines[i].length() / 2, y + i, lines[i]);
		}
	}
}
