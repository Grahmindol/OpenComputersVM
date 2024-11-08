package vm.computer.components;

import org.json.JSONObject;
import vm.computer.Machine;

import java.util.Objects;

public class Tunnel extends NetworkBase {
	public String channel;
	
	public Tunnel(Machine machine, String address, String channel, String wakeUpMessage, boolean wakeUpMessageFuzzy) {
		super(machine, address, "tunnel", wakeUpMessage, wakeUpMessageFuzzy);

		this.channel = channel;
	}

	@Override
	public void pushProxyFields() {
		super.pushProxyFields();

		machine.lua.pushJavaFunction(args -> {
			for (Machine machine : Machine.list) {
				machine.tunnelComponents.forEach(((s, tunnel) -> {
					if (Objects.equals(tunnel.channel, this.channel))
						pushModemMessageSignal(machine, tunnel.address, 0, args, 1);
				}));

			}

			machine.lua.pushBoolean(true);
			return 1;
		});
		machine.lua.setField(-2, "send");
		
		machine.lua.pushJavaFunction(args -> {
			machine.lua.pushString(channel);
			return 1;
		});
		machine.lua.setField(-2, "getChannel");
	}

	@Override
	public JSONObject toJSONObject() {
		return super.toJSONObject()
			.put("channel", channel);
	}
}
