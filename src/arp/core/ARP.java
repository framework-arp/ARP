package arp.core;

public class ARP {

	private static MessageConsumer messageConsumer;

	public static void enhance(String... pkgs) throws Exception {
		ClassEnhancer.enhance(pkgs);
	}

	public static void startMessageConsumer() {
		messageConsumer = new MessageConsumer();
		messageConsumer.start();
	}

	public static void registerMessageProcessor(String processDesc, MessageProcessor processor) {
		messageConsumer.registerProcessor(processDesc, processor);
	}

}
