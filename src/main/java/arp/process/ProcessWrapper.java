package arp.process;

import arp.process.publish.ProcessPublisher;

public class ProcessWrapper {

	public static void beforeProcessStart(String processName) {
		ProcessContext processContext = ThreadBoundProcessContextArray
				.createProcessContext();
		processContext.startProcess(processName);
	}

	public static void afterProcessFinish() {
		ProcessContext processContext = ThreadBoundProcessContextArray
				.getProcessContext();
		processContext.finishProcess();
	}

	public static void publishProcess() {
		ProcessContext processContext = ThreadBoundProcessContextArray
				.getProcessContext();
		ProcessPublisher.publish(processContext.getArguments(),
				processContext.getResult(),
				processContext.getCreatedAggrs(),
				processContext.getDeletedAggrs(),
				processContext.getUpdatedAggrs(),
				processContext.getProcessDesc(),
				processContext.isDontPublishWhenResultIsNull(),
				System.currentTimeMillis());
	}

	public static void afterProcessFaild() {
		ProcessContext processContext = ThreadBoundProcessContextArray
				.getProcessContext();
		processContext.processFaild();
	}

	public static void setPublish(boolean publish) {
		ProcessContext processContext = ThreadBoundProcessContextArray
				.getProcessContext();
		processContext.setPublish(publish);
	}

	public static void recordProcessDesc(String clsName, String mthName,
			String processName) {
		ProcessContext processContext = ThreadBoundProcessContextArray
				.getProcessContext();
		processContext.recordProcessDesc(clsName, mthName, processName);
	}

	public static void recordProcessResult(Object result) {
		ProcessContext processContext = ThreadBoundProcessContextArray
				.getProcessContext();
		processContext.recordProcessResult(result);
	}

	public static void setDontPublishWhenResultIsNull(
			boolean dontPublishWhenResultIsNull) {
		ProcessContext processContext = ThreadBoundProcessContextArray
				.getProcessContext();
		processContext
				.setDontPublishWhenResultIsNull(dontPublishWhenResultIsNull);
	}

	public static void recordProcessArgument(Object argument) {
		ProcessContext processContext = ThreadBoundProcessContextArray
				.getProcessContext();
		processContext.recordProcessArgument(argument);
	}

}
