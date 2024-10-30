package vm.computer.components;

import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import org.json.JSONObject;
import vm.computer.LuaUtils;
import vm.computer.Machine;
import vm.computer.controller.ScreenController;

import java.io.IOException;
import java.util.Objects;

public class Screen extends ComponentWindowed {

	public boolean precise, isOn;
	public int blocksHorizontally, blocksVertically;

	public ScreenController controller;

	public Keyboard keyboard;





	public Screen(Machine machine, String address, boolean precise, int blocksHorizontally, int blocksVertically, String kb) throws IOException {
		super(machine, address, "screen");

		controller = new ScreenController(machine,address);

		FXMLLoader fxmlLoader = new FXMLLoader(Machine.class.getResource("screen.fxml"));
		fxmlLoader.setController(controller);
		if (!Objects.equals(kb, "null")) {
			stage.setTitle("Screen@" + address + " | Keyboard@" + kb);
			this.keyboard = new  Keyboard(machine, kb);
		}else {
			stage.setTitle("Screen@" + address);
			this.keyboard = null;
		}

		stage.setScene(new Scene(fxmlLoader.load()));
		this.precise = precise;
		this.blocksHorizontally = blocksHorizontally;
		this.blocksVertically = blocksVertically;
		this.isOn = true;
	}


	@Override
	public void pushProxyFields() {
		super.pushProxyFields();

		// Number of screen blocks vertically and horizontally
		machine.lua.pushJavaFunction(args -> {
			machine.lua.pushInteger(blocksHorizontally);
			machine.lua.pushInteger(blocksVertically);

			return 2;
		});
		machine.lua.setField(-2, "getAspectRatio");
		
		// A table with one single address for the keyboard component
		machine.lua.pushJavaFunction(args -> {
			machine.lua.newTable();
			int tableIndex = machine.lua.getTop();
			if (keyboard != null) {
				machine.lua.pushInteger(1);
				machine.lua.pushString(this.keyboard.address);
				machine.lua.setTable(tableIndex);
			}

			return 1;
		});
		machine.lua.setField(-2, "getKeyboards");
		
		
		machine.lua.pushJavaFunction(args -> {
			boolean oldValue = precise;
			precise = args.checkBoolean(1);
			machine.lua.pushBoolean(oldValue);
			
			return 1;
		});
		machine.lua.setField(-2, "setPrecise");

		machine.lua.pushJavaFunction(args -> {
			machine.lua.pushBoolean(precise);
			
			return 1;
		});
		machine.lua.setField(-2, "isPrecise");

		machine.lua.pushJavaFunction(args -> {
			machine.lua.pushBoolean(!isOn);
			isOn = true;
			return 1;
		});
		machine.lua.setField(-2, "turnOn");

		machine.lua.pushJavaFunction(args -> {
			machine.lua.pushBoolean(isOn);
			isOn = false;
			return 1;
		});
		machine.lua.setField(-2, "turnOff");

		machine.lua.pushJavaFunction(args -> {
			machine.lua.pushBoolean(isOn);
			return 1;
		});
		machine.lua.setField(-2, "isOn");

		LuaUtils.pushBooleanFunction(machine.lua, "setTouchModeInverted", true);
		LuaUtils.pushBooleanFunction(machine.lua, "isTouchModeInverted", true);
	}

	@Override
	public JSONObject toJSONObject() {
		return super.toJSONObject()
				.put("precise", precise)
				.put("blocksHorizontally", blocksHorizontally)
				.put("blocksVertically", blocksVertically)
				.put("keyboard",keyboard.address);
	}
}
