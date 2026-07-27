package com.osrstcg.persist;

import com.osrstcg.model.TcgState;
import com.osrstcg.persist.TcgSaveMetadataEntry;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;

@Singleton
@Slf4j
public class TcgStateStore
{
	private static final String GROUP = "osrstcg";
	private static final String STATE_KEY = "state";
	private static final String STATE_HASH_KEY = "hash";
	private static final String STATE_BACKUP_KEY = "stateBackup";
	private static final String STATE_BACKUP_HASH_KEY = "hashBackup";
	private static final String STATE_WRITTEN_AT_KEY = "stateWrittenAt";

	private final ConfigManager configManager;
	private final TcgStateCodec stateCodec;
	private final TcgStateFileBackupStore fileBackupStore;

	@Inject
	public TcgStateStore(
		ConfigManager configManager,
		TcgStateCodec stateCodec,
		TcgStateFileBackupStore fileBackupStore)
	{
		this.configManager = configManager;
		this.stateCodec = stateCodec;
		this.fileBackupStore = fileBackupStore;
	}

	TcgStateStore(ConfigManager configManager, TcgStateCodec stateCodec)
	{
		this(configManager, stateCodec, null);
	}

	public TcgStateLoadResult load()
	{
		migrateObsoleteKeysAndSeedDisk();

		LoadAttempt config = tryLoadConfig(STATE_KEY, STATE_HASH_KEY);
		if (config.outcome == LoadOutcome.SUCCESS)
		{
			if (config.missingHash)
			{
				log.info("OSRS TCG state has no integrity hash yet; it will be written on next checkpoint.");
			}

			TcgStateLoadResult newerDisk = newerDiskState(config.state);
			if (newerDisk != null)
			{
				log.warn("OSRS TCG profile configuration is stale (saved {}); restoring newer disk save (saved {}).",
					config.state.getProfileSavedAtUnix(), newerDisk.getState().getProfileSavedAtUnix());
				return newerDisk;
			}

			return new TcgStateLoadResult(config.state, TcgStateLoadSource.CONFIG);
		}

		boolean configFailed = config.outcome != LoadOutcome.MISSING;
		if (configFailed)
		{
			log.warn("OSRS TCG profile configuration state could not be loaded ({}); trying tcg.save.",
				config.outcome);
		}

		Optional<TcgState> master = loadMaster();
		if (master.isPresent())
		{
			log.warn("OSRS TCG restored state from tcg.save after configuration load failed or was missing.");
			return new TcgStateLoadResult(master.get(), TcgStateLoadSource.DISK, configFailed, false);
		}

		Optional<TcgState> snapshot = loadMostRecentSnapshot();
		if (snapshot.isPresent())
		{
			log.warn("OSRS TCG restored state from hash snapshot after configuration and tcg.save failed.");
			return new TcgStateLoadResult(snapshot.get(), TcgStateLoadSource.DISK_SNAPSHOT, configFailed, true);
		}

		if (configFailed)
		{
			log.error("OSRS TCG could not restore state from configuration, tcg.save, or snapshots.");
		}

		return new TcgStateLoadResult(TcgState.empty(), TcgStateLoadSource.EMPTY, configFailed, configFailed);
	}

	/**
	 * Returns the freshest on-disk state when its {@code profileSavedAtUnix} is strictly
	 * newer than the config state's, or null when config is current or has no timestamp.
	 */
	private TcgStateLoadResult newerDiskState(TcgState configState)
	{
		if (fileBackupStore == null || configState.getProfileSavedAtUnix() <= 0)
		{
			return null;
		}

		TcgState best = null;
		TcgStateLoadSource source = null;
		long bestSavedAt = configState.getProfileSavedAtUnix();

		Optional<TcgState> master = fileBackupStore.loadMaster();
		if (master.isPresent() && master.get().getProfileSavedAtUnix() > bestSavedAt)
		{
			best = master.get();
			source = TcgStateLoadSource.DISK;
			bestSavedAt = best.getProfileSavedAtUnix();
		}

		Optional<TcgState> snapshot = fileBackupStore.loadMostRecentSnapshot();
		if (snapshot.isPresent() && snapshot.get().getProfileSavedAtUnix() > bestSavedAt)
		{
			best = snapshot.get();
			source = TcgStateLoadSource.DISK_SNAPSHOT;
		}

		return best == null ? null : new TcgStateLoadResult(best, source);
	}

	public Optional<TcgState> loadMaster()
	{
		if (fileBackupStore == null)
		{
			return Optional.empty();
		}
		return fileBackupStore.loadMaster();
	}

	public Optional<TcgState> loadMostRecentSnapshot()
	{
		if (fileBackupStore == null)
		{
			return Optional.empty();
		}
		return fileBackupStore.loadMostRecentSnapshot();
	}

	public Optional<TcgState> loadByHashPrefix(String prefix)
	{
		if (fileBackupStore == null)
		{
			return Optional.empty();
		}
		return fileBackupStore.loadByHashPrefix(prefix);
	}

	public List<TcgSaveMetadataEntry> listSaveMetadata()
	{
		return listSaveMetadata(null);
	}

	public List<TcgSaveMetadataEntry> listSaveMetadata(String profileDirId)
	{
		if (fileBackupStore == null)
		{
			return List.of();
		}
		return fileBackupStore.listSaveMetadata(profileDirId);
	}

	public Optional<TcgState> loadByFileName(String fileName)
	{
		return loadByFileName(null, fileName);
	}

	public Optional<TcgState> loadByFileName(String profileDirId, String fileName)
	{
		if (fileBackupStore == null)
		{
			return Optional.empty();
		}
		return fileBackupStore.loadByFileName(profileDirId, fileName);
	}

	public List<TcgBackupProfile> listBackupProfiles()
	{
		if (fileBackupStore == null)
		{
			return List.of();
		}
		return fileBackupStore.listBackupProfiles();
	}

	public String currentBackupProfileId()
	{
		if (fileBackupStore == null)
		{
			return TcgStateFileBackupStore.DEFAULT_PROFILE_DIR;
		}
		return fileBackupStore.currentProfileDirName();
	}

	/** @deprecated use {@link #loadMostRecentSnapshot()} */
	@Deprecated
	public Optional<TcgState> loadMostRecentFileBackup()
	{
		return loadMostRecentSnapshot();
	}

	/**
	 * Writes {@code tcg.save} + {@code saves.json} master entry only.
	 */
	public boolean saveMasterOnly(TcgState state, TcgSaveTrigger trigger)
	{
		Encoded encoded = encode(state);
		if (encoded == null || fileBackupStore == null)
		{
			return false;
		}
		return fileBackupStore.writeMaster(encoded.blob, encoded.cardCount, encoded.credits, trigger);
	}

	/**
	 * Writes {@code tcg.save}, hash snapshot, {@code saves.json}, and RSProfile {@code state}/{@code hash}.
	 */
	public boolean saveFullCheckpoint(TcgState state, TcgSaveTrigger trigger)
	{
		Encoded encoded = encode(state);
		if (encoded == null)
		{
			return false;
		}

		boolean diskOk = true;
		if (fileBackupStore != null)
		{
			diskOk = fileBackupStore.writeMaster(encoded.blob, encoded.cardCount, encoded.credits, trigger);
			diskOk = fileBackupStore.writeSnapshot(encoded.blob, encoded.cardCount, encoded.credits, trigger) && diskOk;
		}
		writeConfigCheckpoint(encoded.blob, encoded.hashHex);
		return diskOk;
	}

	/**
	 * Writes hash snapshot + RSProfile {@code state}/{@code hash} without updating {@code tcg.save}.
	 */
	public boolean saveCheckpoint(TcgState state, TcgSaveTrigger trigger)
	{
		Encoded encoded = encode(state);
		if (encoded == null)
		{
			return false;
		}

		boolean diskOk = true;
		if (fileBackupStore != null)
		{
			diskOk = fileBackupStore.writeSnapshot(encoded.blob, encoded.cardCount, encoded.credits, trigger);
		}
		writeConfigCheckpoint(encoded.blob, encoded.hashHex);
		return diskOk;
	}

	/**
	 * Writes RSProfile {@code state}/{@code hash} only (no disk snapshot / master).
	 * Used after a validated load so config matches the in-memory collection.
	 */
	public boolean saveConfigOnly(TcgState state)
	{
		Encoded encoded = encode(state);
		if (encoded == null)
		{
			return false;
		}
		writeConfigCheckpoint(encoded.blob, encoded.hashHex);
		return true;
	}

	/** @deprecated use {@link #saveCheckpoint} or {@link #saveFullCheckpoint} */
	@Deprecated
	public boolean saveToFileBackup(TcgState state)
	{
		Encoded encoded = encode(state);
		if (encoded == null || fileBackupStore == null)
		{
			return false;
		}
		return fileBackupStore.writeSnapshot(encoded.blob, encoded.cardCount, encoded.credits, TcgSaveTrigger.MANUAL);
	}

	/**
	 * @deprecated use {@link #saveFullCheckpoint} / {@link #saveCheckpoint}
	 */
	@Deprecated
	public void save(TcgState state)
	{
		saveFullCheckpoint(state, TcgSaveTrigger.LOGOUT);
	}

	private void writeConfigCheckpoint(String stored, String hashHex)
	{
		writeProfileScoped(STATE_KEY, stored);
		writeProfileScoped(STATE_HASH_KEY, hashHex);

		String roundTrip = getProfileScoped(STATE_KEY);
		String roundTripHash = getProfileScoped(STATE_HASH_KEY);
		if (!Objects.equals(stored, roundTrip))
		{
			log.error("OSRS TCG state save verification failed: stored payload mismatch after write.");
		}
		else if (roundTripHash == null || !hashHex.equalsIgnoreCase(roundTripHash.trim()))
		{
			log.error("OSRS TCG state save verification failed: hash mismatch after write.");
		}
	}

	private Encoded encode(TcgState state)
	{
		if (state == null)
		{
			return null;
		}
		String json = stateCodec.toJson(state);
		String stored = TcgStateStorageEncoding.encode(json);
		if (stored.isEmpty())
		{
			log.error("OSRS TCG state save aborted: encoding produced an empty payload.");
			return null;
		}
		int cardCount = state.getCollectionState().getOwnedInstances().size();
		long credits = state.getEconomyState().getCredits();
		String hashHex = TcgStateHash.hexOfUtf8(stored);
		return new Encoded(stored, hashHex, cardCount, credits);
	}

	/**
	 * Seeds disk from config/backup when needed and unsets obsolete backup keys.
	 */
	void migrateObsoleteKeysAndSeedDisk()
	{
		moveOldStateIntoProfile();

		boolean hasMaster = fileBackupStore != null && fileBackupStore.loadMaster().isPresent();
		if (!hasMaster && fileBackupStore != null)
		{
			LoadAttempt primary = tryLoadConfig(STATE_KEY, STATE_HASH_KEY);
			LoadAttempt backup = primary.outcome == LoadOutcome.SUCCESS
				? primary
				: tryLoadConfig(STATE_BACKUP_KEY, STATE_BACKUP_HASH_KEY);
			if (backup.outcome == LoadOutcome.SUCCESS)
			{
				TcgState seed = backup.state;
				Optional<TcgState> newestSnapshot = fileBackupStore.loadMostRecentSnapshot();
				if (newestSnapshot.isPresent()
					&& seed.getProfileSavedAtUnix() > 0
					&& newestSnapshot.get().getProfileSavedAtUnix() > seed.getProfileSavedAtUnix())
				{
					log.warn("OSRS TCG migration: newest disk snapshot (saved {}) is newer than profile configuration (saved {}); seeding tcg.save from the snapshot.",
						newestSnapshot.get().getProfileSavedAtUnix(), seed.getProfileSavedAtUnix());
					seed = newestSnapshot.get();
				}

				String json = stateCodec.toJson(seed);
				String stored = TcgStateStorageEncoding.encode(json);
				if (!stored.isEmpty())
				{
					int cardCount = seed.getCollectionState().getOwnedInstances().size();
					long credits = seed.getEconomyState().getCredits();
					fileBackupStore.writeMaster(stored, cardCount, credits, TcgSaveTrigger.MIGRATION);
					fileBackupStore.writeSnapshot(stored, cardCount, credits, TcgSaveTrigger.MIGRATION);
					log.info("OSRS TCG seeded disk saves during migration.");
				}
			}
		}

		unsetObsoleteKeys();
		if (fileBackupStore != null)
		{
			fileBackupStore.rewriteSavesIndexFromDisk();
		}
	}

	private void unsetObsoleteKeys()
	{
		unsetProfileScoped(STATE_BACKUP_KEY);
		unsetProfileScoped(STATE_BACKUP_HASH_KEY);
		unsetProfileScoped(STATE_WRITTEN_AT_KEY);
		unsetGlobalScoped(STATE_BACKUP_KEY);
		unsetGlobalScoped(STATE_BACKUP_HASH_KEY);
		unsetGlobalScoped(STATE_WRITTEN_AT_KEY);
	}

	private LoadAttempt tryLoadConfig(String stateKey, String hashKey)
	{
		String rawState = getProfileScoped(stateKey);
		if (rawState == null || rawState.isEmpty())
		{
			return LoadAttempt.missing();
		}

		String expectedHex = getProfileScoped(hashKey);
		boolean missingHash = expectedHex == null || expectedHex.isEmpty();
		if (!missingHash)
		{
			String actualHex = TcgStateHash.hexOfUtf8(rawState);
			if (!actualHex.equalsIgnoreCase(expectedHex.trim()))
			{
				return LoadAttempt.hashMismatch();
			}
		}

		String json = TcgStateStorageEncoding.decode(rawState);
		if (json.isEmpty())
		{
			return LoadAttempt.decodeFailed();
		}

		Optional<TcgState> parsed = stateCodec.tryFromJson(json);
		if (parsed.isEmpty())
		{
			return LoadAttempt.decodeFailed();
		}

		return LoadAttempt.success(parsed.get(), missingHash);
	}

	void writeProfileScoped(String key, String value)
	{
		configManager.setRSProfileConfiguration(GROUP, key, value);
	}

	String getProfileScoped(String key)
	{
		return configManager.getRSProfileConfiguration(GROUP, key);
	}

	void unsetProfileScoped(String key)
	{
		configManager.unsetRSProfileConfiguration(GROUP, key);
	}

	String getGlobalScoped(String key)
	{
		return configManager.getConfiguration(GROUP, key);
	}

	void writeGlobalScoped(String key, String value)
	{
		configManager.setConfiguration(GROUP, key, value);
	}

	void unsetGlobalScoped(String key)
	{
		configManager.unsetConfiguration(GROUP, key);
	}

	void moveOldStateIntoProfile()
	{
		String currentState = getProfileScoped(STATE_KEY);
		if (currentState != null)
		{
			return;
		}

		String currentBackup = getProfileScoped(STATE_BACKUP_KEY);
		if (currentBackup != null)
		{
			return;
		}

		String oldState = getGlobalScoped(STATE_KEY);
		String oldBackup = getGlobalScoped(STATE_BACKUP_KEY);
		if (oldState == null && oldBackup == null)
		{
			return;
		}

		if (oldState != null)
		{
			writeProfileScoped(STATE_KEY, oldState);
			if (!oldState.equals(getProfileScoped(STATE_KEY)))
			{
				return;
			}
			moveOldHash(STATE_HASH_KEY);
		}

		if (oldBackup != null)
		{
			writeProfileScoped(STATE_BACKUP_KEY, oldBackup);
			if (!oldBackup.equals(getProfileScoped(STATE_BACKUP_KEY)))
			{
				return;
			}
			moveOldHash(STATE_BACKUP_HASH_KEY);
		}

		unsetGlobalScoped(STATE_KEY);
		unsetGlobalScoped(STATE_HASH_KEY);
		unsetGlobalScoped(STATE_BACKUP_KEY);
		unsetGlobalScoped(STATE_BACKUP_HASH_KEY);
	}

	private void moveOldHash(String key)
	{
		String value = getGlobalScoped(key);
		if (value != null)
		{
			writeProfileScoped(key, value);
		}
	}

	private enum LoadOutcome
	{
		SUCCESS,
		MISSING,
		HASH_MISMATCH,
		DECODE_FAILED
	}

	private static final class Encoded
	{
		private final String blob;
		private final String hashHex;
		private final int cardCount;
		private final long credits;

		private Encoded(String blob, String hashHex, int cardCount, long credits)
		{
			this.blob = blob;
			this.hashHex = hashHex;
			this.cardCount = cardCount;
			this.credits = credits;
		}
	}

	private static final class LoadAttempt
	{
		private final LoadOutcome outcome;
		private final TcgState state;
		private final boolean missingHash;

		private LoadAttempt(LoadOutcome outcome, TcgState state, boolean missingHash)
		{
			this.outcome = outcome;
			this.state = state;
			this.missingHash = missingHash;
		}

		private static LoadAttempt missing()
		{
			return new LoadAttempt(LoadOutcome.MISSING, TcgState.empty(), false);
		}

		private static LoadAttempt hashMismatch()
		{
			return new LoadAttempt(LoadOutcome.HASH_MISMATCH, TcgState.empty(), false);
		}

		private static LoadAttempt decodeFailed()
		{
			return new LoadAttempt(LoadOutcome.DECODE_FAILED, TcgState.empty(), false);
		}

		private static LoadAttempt success(TcgState state, boolean missingHash)
		{
			return new LoadAttempt(LoadOutcome.SUCCESS, state, missingHash);
		}
	}
}
