package arp.process;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import arp.enhance.ProcessInfo;
import arp.repository.Repository;
import arp.repository.RepositoryProcessEntities;
import arp.util.Unsafe;

public class ProcessContext {

	private static ProcessInfo[] processInfos;

	public static void setProcessInfos(List<ProcessInfo> processInfoList) {
		processInfos = new ProcessInfo[processInfoList.size()];
		for (ProcessInfo processInfo : processInfoList) {
			processInfos[processInfo.getId()] = processInfo;
		}
	}

	public static ProcessInfo getProcessInfo(int processInfoId) {
		return processInfos[processInfoId];
	}

	private boolean started;

	private Map<Integer, RepositoryProcessEntities<?, ?>> processEntities = new HashMap<>();

	private List<AtomicInteger> singleEntityAcquiredLocks = new ArrayList<>();

	private List<Object> arguments = new ArrayList<>();

	private Object result;

	private List<Object> createdAggrs = new ArrayList<>();
	private List<Object> deletedAggrs = new ArrayList<>();
	private List<Object[]> updatedAggrs = new ArrayList<>();

	private boolean dontPublishWhenResultIsNull;

	private String processDesc;

	private boolean publish;

	private int processInfoId;

	private Map<String, Object> contextParameters = new HashMap<>();

	private List<Map<String, Object>> contextParametersTrace = new ArrayList<>();

	public void startProcess(int processInfoId) {
		if (started) {
			throw new RuntimeException(
					"can not start a process in another started process");
		}
		this.processInfoId = processInfoId;
		started = true;
		Unsafe.loadFence();
	}

	public void finishProcess() {
		Unsafe.storeFence();
		try {
			flushProcessEntities();
		} catch (Exception e) {
			try {
				releaseAcquiredLocks();
			} catch (Exception e1) {
			}
			clear();
			started = false;
			throw new RuntimeException("flush process entities faild", e);
		}
		try {
			releaseAcquiredLocks();
		} catch (Exception e) {
		}
		started = false;
	}

	private void flushProcessEntities() throws Exception {
		for (RepositoryProcessEntities entities : processEntities.values()) {
			Repository repository = Repository.getRepository(entities
					.getRepositoryId());
			Map processEntities = entities.getEntities();
			Map entitiesToCreate = new HashMap();
			Map entitiesToUpdate = new HashMap();
			Set idsToRemove = new HashSet();

			for (Object obj : processEntities.entrySet()) {
				Entry entry = (Entry) obj;
				Object id = entry.getKey();
				ProcessEntity processEntity = (ProcessEntity) entry.getValue();
				if (processEntity.getState() instanceof CreatedProcessEntityState) {
					entitiesToCreate.put(id, processEntity.getEntity());
					createdAggrs.add(processEntity.getEntity());
				} else if (processEntity.getState() instanceof TakenProcessEntityState) {
					if (processEntity.changed()) {
						entitiesToUpdate.put(id, processEntity.getEntity());
						updatedAggrs.add(new Object[] {
								processEntity.getInitialEntitySnapshot(),
								processEntity.getEntity() });
					}
				} else if (processEntity.getState() instanceof RemovedProcessEntityState) {
					idsToRemove.add(id);
					deletedAggrs.add(processEntity.getEntity());
				}
			}
			if (!idsToRemove.isEmpty()) {
				repository.deleteEntities(idsToRemove);
			}
			if (!entitiesToUpdate.isEmpty()) {
				repository.updateEntities(entitiesToUpdate);
			}
			if (!entitiesToCreate.isEmpty()) {
				repository.createEntities(entitiesToCreate);
			}
		}
	}

	public boolean isStarted() {
		return started;
	}

	public <I, E> ProcessEntity<E> getEntityInProcessForTake(int repositoryId,
			I entityId) {
		RepositoryProcessEntities<I, E> entities = (RepositoryProcessEntities<I, E>) processEntities
				.get(repositoryId);
		if (entities == null) {
			return null;
		}
		return entities.takeEntity(entityId);
	}

	public <I, E> E copyEntityInProcess(int repositoryId, I entityId) {
		RepositoryProcessEntities<I, E> entities = (RepositoryProcessEntities<I, E>) processEntities
				.get(repositoryId);
		if (entities == null) {
			return null;
		}
		return entities.copyEntity(entityId);
	}

	public <I, E> void takeEntityFromRepoAndPutInProcess(int repositoryId,
			I entityId, E entity) {
		RepositoryProcessEntities<I, E> entities = (RepositoryProcessEntities<I, E>) processEntities
				.get(repositoryId);
		if (entities == null) {
			entities = new RepositoryProcessEntities<>(repositoryId);
			processEntities.put(repositoryId, entities);
		}
		entities.takeEntityFromRepoAndPutInProcess(entityId, entity);
	}

	public <I, E> void takeEntityFromRepoAndPutInProcessAsRemoved(
			int repositoryId, I entityId, E entity) {
		RepositoryProcessEntities<I, E> entities = (RepositoryProcessEntities<I, E>) processEntities
				.get(repositoryId);
		if (entities == null) {
			entities = new RepositoryProcessEntities<>(repositoryId);
			processEntities.put(repositoryId, entities);
		}
		entities.takeEntityFromRepoAndPutInProcessAsRemoved(entityId, entity);
	}

	public <I, E> void putEntityInProcess(int repositoryId, I entityId, E entity) {
		RepositoryProcessEntities<I, E> entities = (RepositoryProcessEntities<I, E>) processEntities
				.get(repositoryId);
		if (entities == null) {
			entities = new RepositoryProcessEntities<>(repositoryId);
			processEntities.put(repositoryId, entities);
		}
		entities.putEntityInProcess(entityId, entity);
	}

	public <I, E> ProcessEntity<E> putIfAbsentEntityInProcess(int repositoryId,
			I entityId, E entity) {
		RepositoryProcessEntities<I, E> entities = (RepositoryProcessEntities<I, E>) processEntities
				.get(repositoryId);
		if (entities == null) {
			return null;
		}
		ProcessEntity<E> processEntity = entities.findEntity(entityId);
		if (processEntity == null) {
			return null;
		}
		if (processEntity.getState() instanceof RemovedProcessEntityState) {
			processEntity.setEntity(entity);
			processEntity.updateStateByPut();
			return processEntity;
		} else if (processEntity.getState() instanceof TransientProcessEntityState) {
			return null;
		} else {
			return processEntity;
		}
	}

	public <I, E> ProcessEntity<E> removeEntityInProcess(int repositoryId,
			I entityId) {
		RepositoryProcessEntities<I, E> entities = (RepositoryProcessEntities<I, E>) processEntities
				.get(repositoryId);
		if (entities == null) {
			return null;
		}
		ProcessEntity<E> processEntity = entities.findEntity(entityId);
		if (processEntity == null) {
			return null;
		}
		if (processEntity.getState() instanceof TransientProcessEntityState) {
			return null;
		}
		processEntity.updateStateByRemove();
		return processEntity;
	}

	public void processFaild() {
		try {
			releaseAcquiredLocks();
		} catch (Exception e) {
		}
		clear();
		started = false;
	}

	public void clear() {
		processEntities.clear();
		arguments.clear();
		createdAggrs.clear();
		deletedAggrs.clear();
		updatedAggrs.clear();
		result = null;
		dontPublishWhenResultIsNull = false;
		processDesc = null;
		publish = false;
	}

	private void releaseAcquiredLocks() throws Exception {
		for (RepositoryProcessEntities entities : processEntities.values()) {
			Repository repository = Repository.getRepository(entities
					.getRepositoryId());

			Map processEntities = entities.getEntities();
			Set idsToUnlock = new HashSet();

			for (Object obj : processEntities.entrySet()) {
				Entry entry = (Entry) obj;
				Object id = entry.getKey();
				ProcessEntity processEntity = (ProcessEntity) entry.getValue();
				if (processEntity.getState() instanceof TakenProcessEntityState) {
					idsToUnlock.add(id);
				} else if (processEntity.getState() instanceof RemovedProcessEntityState) {
					idsToUnlock.add(id);
				}
			}

			repository.returnEntities(idsToUnlock);

		}
		for (AtomicInteger lock : singleEntityAcquiredLocks) {
			lock.set(0);
		}
	}

	public void addSingleEntityAcquiredLock(AtomicInteger lock) {
		singleEntityAcquiredLocks.add(lock);
	}

	public void recordProcessResult(Object result) {
		this.result = result;
	}

	public void setDontPublishWhenResultIsNull(
			boolean dontPublishWhenResultIsNull) {
		this.dontPublishWhenResultIsNull = dontPublishWhenResultIsNull;
	}

	public void recordProcessDesc(String clsName, String mthName,
			String processName) {
		if (!processName.trim().isEmpty()) {
			processDesc = processName;
		} else {
			processDesc = clsName + "." + mthName;
		}
	}

	public void addCreatedAggr(Object createdAggr) {
		createdAggrs.add(createdAggr);
	}

	public Object getContextParameter(String key) {
		return contextParameters.get(key);
	}

	public void addContextParameter(String key, Object value) {
		contextParameters.put(key, value);
	}

	public Object getResult() {
		return result;
	}

	public boolean isDontPublishWhenResultIsNull() {
		return dontPublishWhenResultIsNull;
	}

	public String getProcessDesc() {
		return processDesc;
	}

	public boolean isPublish() {
		return publish;
	}

	public void setPublish(boolean publish) {
		this.publish = publish;
	}

	public void recordProcessArgument(Object argument) {
		arguments.add(argument);
	}

	public List<Object> getArguments() {
		return arguments;
	}

	public List<Object[]> getUpdatedAggrs() {
		return updatedAggrs;
	}

	public List<Object> getCreatedAggrs() {
		return createdAggrs;
	}

	public List<Object> getDeletedAggrs() {
		return deletedAggrs;
	}

	public int getProcessInfoId() {
		return processInfoId;
	}

	public ProcessInfo getProcessInfo() {
		return processInfos[processInfoId];
	}

	public Map<String, Object> getContextParameters() {
		return contextParameters;
	}

	public List<Map<String, Object>> getContextParametersTrace() {
		return contextParametersTrace;
	}

	public void setContextParametersTrace(
			List<Map<String, Object>> contextParametersTrace) {
		this.contextParametersTrace = contextParametersTrace;
	}

	public List<Map<String, Object>> buildContextParametersTrace() {
		List<Map<String, Object>> newTrace = new ArrayList<>(
				contextParametersTrace);
		newTrace.add(contextParameters);
		return newTrace;
	}

}
