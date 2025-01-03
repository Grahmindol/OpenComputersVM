package vm.computer.components;

import li.cil.repack.com.naef.jnlua.LuaState;
import org.json.JSONObject;
import vm.computer.Machine;
import vm.computer.components.base.ComponentBase;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.*;
import java.util.Map;

public class Internet extends ComponentBase {
	public static final int defaultReadLength = 1024;
	
	public Internet(Machine machine, String address, JSONObject obj) {
		super(machine, address, obj);
	}

	@SuppressWarnings("unchecked")
	@Override
	public void pushProxyFields() {
		super.pushProxyFields();

		machine.lua.pushJavaFunction(connectArgs -> {
			try {
				System.out.println("Opening TCP client connection: " + connectArgs.checkString(1) + ":" + connectArgs.checkInteger(2));
				Socket socket = new Socket(connectArgs.checkString(1), connectArgs.isNoneOrNil(2) ? 80 : connectArgs.checkInteger(2));
				
				System.out.println("Connection estabilished: " + socket.getRemoteSocketAddress());
				socket.setSoTimeout(100);

				OutputStream outputStream = socket.getOutputStream();
				InputStream inputStream = socket.getInputStream();
				
				machine.lua.newTable();
				
				// Lecture depuis une prise
				machine.lua.pushJavaFunction(readArgs -> {
					try {
//						System.out.println("READING STARTED");
						byte[] buffer = new byte[getArraySize(readArgs)];
						int readCount = inputStream.read(buffer);
//							System.out.println("READING FINISED, READ CUNT: " + readCount);

						if (readCount > 0) {
							byte[] result = new byte[readCount];
							System.arraycopy(buffer, 0, result, 0, readCount);

							machine.lua.pushByteArray(result);
							return 1;
						}
						else {
							return pushEmptyArray();
						}
					}
					catch (SocketTimeoutException e) {
						return pushEmptyArray();
					}
					catch (IOException e) {
						return pushIOException(e.getMessage());
					}
				});
				machine.lua.setField(-2, "read");

				// Write to socket
				machine.lua.pushJavaFunction(readArgs -> {
					try {
						byte[] data = readArgs.checkByteArray(1);
						outputStream.write(data);
						
						machine.lua.pushInteger(data.length);
						
						return 1;
					}
					catch (IOException e) {
						return pushIOException(e.getMessage());
					}
				});
				machine.lua.setField(-2, "write");

				// Closing a socket
				machine.lua.pushJavaFunction(args -> {
					try {
						socket.close();
						inputStream.close();
						outputStream.close();
					}
					catch (IOException ignored) {}
					finally {
						machine.lua.pushBoolean(true);
					}
					return 1;
				});
				machine.lua.setField(-2, "close");

				// check the connection status
				machine.lua.pushJavaFunction(args -> {
					machine.lua.pushBoolean(socket.isConnected());
					return 1;
				});
				machine.lua.setField(-2, "finishConnect");
				
				return 1;
			}
			catch (IOException e) {
				return pushIOException(e.getMessage());
			}
		});
		machine.lua.setField(-2, "connect");
		
		machine.lua.pushJavaFunction(requestArgs -> {
			try {
				System.out.println("Opening HTTP connection: " + requestArgs.checkString(1));
				HttpURLConnection connection = (HttpURLConnection) new URL(requestArgs.checkString(1)).openConnection();

				connection.setDoInput(true);
				
				// We cut the headers
				if (!requestArgs.isNoneOrNil(3) && requestArgs.isTable(3)) {
					((Map<String, String>) requestArgs.toJavaObject(3, Map.class)).forEach((key, value) -> {
//						System.out.println("Setting header: "+ key.toString() + " : " + value.toString());
						connection.setRequestProperty(key.toString(), value.toString());
					});
				}
				
				// We hook up the request method
				if (!requestArgs.isNoneOrNil(2)) {
					String postData = requestArgs.checkString(2);

					connection.setRequestMethod("POST");

					connection.setDoOutput(true);
					connection.setReadTimeout(1000);
					
					OutputStream outputStream = connection.getOutputStream();
					outputStream.write(postData.getBytes());
					outputStream.close();
				}
				else {
					connection.setRequestMethod("GET");
				}

				// We're hooking up the reader
				InputStream inputStream = connection.getInputStream();
				
				machine.lua.newTable();
				
				// Reading from http request
				machine.lua.pushJavaFunction(readArgs -> {
					try {
//						System.out.println(", available: " + inputStream.available());
						byte[] buffer = new byte[getArraySize(readArgs)];
						int readCount = inputStream.read(buffer);
//						System.out.println("Buffer size: " + buffer.length + ", readCount: " + readCount);
						
						if (readCount > 0) {
							byte[] result = new byte[readCount];
							System.arraycopy(buffer, 0, result, 0, readCount);

							machine.lua.pushByteArray(result);
							return 1;
						}
						else {
							machine.lua.pushNil();
							return 1;
						}
					}
					catch (IOException e) {
						return pushIOException(e.getMessage());
					}
				});
				machine.lua.setField(-2, "read");

				// Closing http request
				machine.lua.pushJavaFunction(args -> {
					try {
						inputStream.close();
						connection.disconnect();
					}
					catch (IOException ignored) {}
					finally {
						machine.lua.pushBoolean(true);
					}
					return 1;
				});
				machine.lua.setField(-2, "close");

				// check the connection status
				machine.lua.pushJavaFunction(args -> {
					try {
						machine.lua.pushBoolean(connection.getResponseCode() > 0);
					} catch (IOException e) {
						machine.lua.pushBoolean(false);
					}
					return 1;
				});
				machine.lua.setField(-2, "finishConnect");

				// Closing http request
				machine.lua.pushJavaFunction(readArgs -> {
					try {
						machine.lua.pushInteger(connection.getResponseCode());
						machine.lua.pushString(connection.getResponseMessage());

						// Пушим поля заголовка вида Map<String, List<String>>
						machine.lua.newTable();
						int tableIndex = machine.lua.getTop();
						
						connection.getHeaderFields().forEach((key, list) -> {
//							System.out.println("Map key: " + key);
//							for (int i = 0; i < list.size(); i++) {
//								System.out.println("Map List: " + i + ", " + list.get(i));
//							}
							
							// В душе не ебу, с хуев ли тут может быть нулл, но бывает. Видимо, это тонкая задумка разрабов
							if (key != null) {
								// Ключ мапы
								machine.lua.pushString(key);

								// Валуя мапы (тот самый List<String>)
								machine.lua.newTable();
								int listIndex = machine.lua.getTop();

								for (int i = 0; i < list.size(); i++) {
									machine.lua.pushInteger(i + 1);
									machine.lua.pushString(list.get(i));
									//Впездываем ключи листа
									machine.lua.setTable(listIndex);
								}

								// Впездываем валую
								machine.lua.setTable(tableIndex);	
							}
						});
						
						return 3;
					}
					catch (IOException e) {
						e.printStackTrace();
						return pushIOException(e.getMessage());
					}
				});
				machine.lua.setField(-2, "response");
				
				return 1;
			}
			catch (Exception e) {
				e.printStackTrace();
				return 0;
			}
		});
		machine.lua.setField(-2, "request");
	}
	
	private int getArraySize(LuaState args) {
		return args.isNoneOrNil(1) ? defaultReadLength : (int) Math.min(defaultReadLength, args.checkNumber(1));
	}
	
	private int pushEmptyArray() {
		machine.lua.pushByteArray(new byte[0]);
		return 1;
	}

	private int pushIOException(String message) {
		machine.lua.pushNil();
		machine.lua.pushString("IOException: " + message);
		return 2;
	}
	
	@Override
	public JSONObject toJSONObject() {
		return super.toJSONObject();
	}
}
