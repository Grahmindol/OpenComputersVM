package vm.computer.components.base;

import li.cil.repack.com.naef.jnlua.LuaState;
import org.json.JSONObject;
import vm.computer.LuaUtils;
import vm.computer.Machine;

public class NetworkBase extends ComponentBase {
	public int maxPacketSize = 8192;
	public String wakeMessage;
	public boolean wakeMessageFuzzy;

	public NetworkBase(Machine machine, String address, JSONObject obj) {
		super(machine, address, obj);
		
		this.wakeMessage = obj.optString("wakeMessage","");
		this.wakeMessageFuzzy = obj.optBoolean("wakeMessageFuzzy",false);
	}

	@Override
	public void pushProxyFields() {
		super.pushProxyFields();

		machine.lua.pushJavaFunction(args -> {
			args.checkString(1);
			
			wakeMessageFuzzy = args.isNoneOrNil(2) ? false : args.checkBoolean(2);
			wakeMessage = args.toString(1);
			
			machine.lua.pushString(wakeMessage);
			return 1;
		});
		machine.lua.setField(-2, "setWakeMessage");
		
		machine.lua.pushJavaFunction(args -> {
			machine.lua.pushString(wakeMessage);
			return 1;
		});
		machine.lua.setField(-2, "getWakeMessage");

		machine.lua.pushJavaFunction(args -> {
			machine.lua.pushInteger(maxPacketSize);
			return 1;
		});
		machine.lua.setField(-2, "maxPacketSize");
	}

	@Override
	public JSONObject toJSONObject() {
		return super.toJSONObject()
			.put("wakeMessage", wakeMessage)
			.put("wakeMessageFuzzy", wakeMessageFuzzy);
	}
	
	public void pushModemMessageSignal(Machine machine, String remoteAddress, int port, LuaState message, int fromIndex) {
		// Нуачо, нах себе-то слать
		if (!remoteAddress.equals(address)) {
			LuaState signal = new LuaState();
			
			signal.pushString("modem_message");
			signal.pushString(remoteAddress);
			signal.pushString(address);
			signal.pushInteger(port);
			signal.pushInteger(0);
			LuaUtils.pushSignalData(signal, message, fromIndex, message.getTop());

			machine.luaThread.pushSignal(signal);
		}
	}
}
