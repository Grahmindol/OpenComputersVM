package vm.computer.components.base;

import li.cil.repack.com.naef.jnlua.LuaState;
import org.json.JSONObject;
import vm.computer.Machine;

public class ComponentBase {
	public int proxyReference;
	public String address, type;
	public Machine machine;
	
	public ComponentBase(Machine machine, String address, JSONObject obj) {
		this.machine = machine;
		this.type = obj.getString("type");
		this.address = address;
	}
	
	public void pushProxy() {
        machine.lua.newTable();
        pushProxyFields();
        proxyReference = machine.lua.ref(LuaState.REGISTRYINDEX);
    }
	
	public void pushProxyFields() {
		machine.lua.pushString(address);
		machine.lua.setField(-2, "address");

		machine.lua.pushString(type);
		machine.lua.setField(-2, "type");

		machine.lua.pushInteger(-1);
		machine.lua.setField(-2, "slot");
	}

	public JSONObject toJSONObject() {
		return new JSONObject()
			.put("type", type)
			.put("address", address);
	}
}
