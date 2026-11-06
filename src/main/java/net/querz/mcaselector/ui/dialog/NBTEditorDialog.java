package net.querz.mcaselector.ui.dialog;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.geometry.Pos;
import javafx.collections.FXCollections;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.Tooltip;
import javafx.util.StringConverter;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import net.querz.mcaselector.io.CacheHelper;
import net.querz.mcaselector.io.mca.CompressionType;
import net.querz.mcaselector.io.FileHelper;
import net.querz.mcaselector.io.mca.Chunk;
import net.querz.mcaselector.io.mca.EntitiesChunk;
import net.querz.mcaselector.io.mca.EntitiesMCAFile;
import net.querz.mcaselector.io.mca.MCAFile;
import net.querz.mcaselector.io.mca.PoiChunk;
import net.querz.mcaselector.io.mca.PoiMCAFile;
import net.querz.mcaselector.io.mca.RegionChunk;
import net.querz.mcaselector.io.mca.RegionMCAFile;
import net.querz.mcaselector.util.point.Point2i;
import net.querz.mcaselector.util.progress.Timer;
import net.querz.mcaselector.util.property.DataProperty;
import net.querz.mcaselector.selection.ChunkSet;
import net.querz.mcaselector.selection.Selection;
import net.querz.mcaselector.text.Translation;
import net.querz.mcaselector.tile.TileMap;
import net.querz.mcaselector.nbtstudio.NbtQuery;
import net.querz.mcaselector.nbtstudio.NbtSearcher;
import net.querz.mcaselector.ui.component.NBTEditorOptionsBar;
import net.querz.mcaselector.ui.component.NBTFindBar;
import net.querz.mcaselector.ui.component.NBTTreeView;
import net.querz.mcaselector.ui.UIFactory;
import net.querz.mcaselector.ui.component.PersistentDialogProperties;
import net.querz.nbt.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import static net.querz.nbt.Tag.Type.*;

public class NBTEditorDialog extends Dialog<NBTEditorDialog.Result> implements PersistentDialogProperties {

	private static final Logger LOGGER = LogManager.getLogger(NBTEditorDialog.class);

	private final Label treeViewPlaceHolder = UIFactory.label(Translation.DIALOG_EDIT_NBT_PLACEHOLDER_LOADING);
	private final TabPane editors = new TabPane();

	private CompoundTag regionData, poiData, entitiesData;
	// the chunk that is shown right now. A search can hit many chunks while the editor shows one
	// at a time, and the arrows in the header walk that list, so this is not final
	private Point2i selectedChunk;
	private final Stage primaryStage;
	// every chunk the search that opened this editor matched. Empty when the editor was opened
	// without a search, and then there is nothing to walk through
	private final List<Point2i> hitChunks;
	private int hitIndex;
	private final Label chunkPositionLabel = new Label();
	private HBox chunkNav;
	// every hit as a drop down, so a chunk far down the list can be jumped to directly
	private ComboBox<Point2i> picker;
	// true while the header updates the picker itself, so setting the value does not load the chunk
	// that is already open a second time
	private boolean syncingPicker;
	// the data as it was loaded, to notice edits before switching to the next chunk throws them away
	private final Map<Integer, CompoundTag> loadedDataByRootType = new LinkedHashMap<>();
	// bumped on every chunk switch, so loader threads of the chunk shown before cannot report into
	// the chunk shown now. Read from loader threads, therefore volatile
	private volatile int loadGeneration;
	// a query that is filled into the find bar of the matching tab as soon as the chunk is loaded,
	// so a search started in the NBT Studio dialog continues right here in the tree
	private final NbtQuery prefill;
	// the last query that was handed to the editor, remembered so that "edit chunk" opened from
	// the map right after an NBT Studio search still shows and expands the hits of that search
	// instead of an empty find bar
	private static NbtQuery lastFindQuery;

	// the three tabs load asynchronously and any of them can hold the hits of a search: the editor
	// waits until every tab reported back, then switches to the first tab that actually contains
	// matches instead of always leaving the region tab in front
	private final Map<Integer, Tab> tabsByRootType = new LinkedHashMap<>();
	private final Map<Integer, NBTFindBar> findBarsByRootType = new LinkedHashMap<>();
	private final Map<Integer, Integer> hitsByRootType = new LinkedHashMap<>();
	private final Object loadLock = new Object();
	private int loadedTabs;
	private boolean tabResolved;

	public NBTEditorDialog(TileMap tileMap, Stage primaryStage) {
		this(tileMap, primaryStage, null);
	}

	public NBTEditorDialog(TileMap tileMap, Stage primaryStage, NbtQuery prefill) {
		this(tileMap, primaryStage, prefill, null, null);
	}

	/**
	 * Opens the given chunk. A find in the NBT Studio dialog can match more than one chunk, and the
	 * editor is tied to a single chunk, so the hit that is opened has to be passed in explicitly
	 * instead of being read from the selection.
	 */
	public NBTEditorDialog(TileMap tileMap, Stage primaryStage, NbtQuery prefill, Point2i chunk) {
		this(tileMap, primaryStage, prefill, chunk, null);
	}

	/**
	 * Opens one chunk of a search result and keeps the whole hit list, so the header arrows can
	 * walk to the other chunks that matched instead of forcing a new search per chunk.
	 */
	public NBTEditorDialog(TileMap tileMap, Stage primaryStage, NbtQuery prefill, Point2i chunk, List<Point2i> hitChunks) {
		this.primaryStage = primaryStage;
		this.hitChunks = hitChunks == null ? new ArrayList<>() : new ArrayList<>(hitChunks);
		// a plain "edit chunk" right after a search has no query of its own, so it falls back to
		// the query of that search: the hits are highlighted and expanded just like in the editor
		// that the search itself opened
		this.prefill = prefill != null ? prefill : lastFindQuery;
		if (prefill != null) {
			lastFindQuery = prefill;
		}
		titleProperty().bind(Translation.DIALOG_EDIT_NBT_TITLE.getProperty());
		initStyle(StageStyle.UTILITY);
		getDialogPane().getStyleClass().add("nbt-editor-dialog-pane");
		setResultConverter(p -> p == ButtonType.APPLY ? new Result(regionData, poiData, entitiesData) : null);
		getDialogPane().getStylesheets().addAll(primaryStage.getScene().getStylesheets());
		getDialogPane().getScene().getStylesheets().addAll(primaryStage.getScene().getStylesheets());
		getDialogPane().getScene().getStylesheets().add(Objects.requireNonNull(NBTEditorDialog.class.getClassLoader().getResource("style/component/nbt-editor-dialog.css")).toExternalForm());
		getDialogPane().getButtonTypes().addAll(ButtonType.APPLY, ButtonType.CANCEL);
		getDialogPane().lookupButton(ButtonType.APPLY).setDisable(true);

		selectedChunk = chunk != null ? chunk : getSelectedChunk(tileMap);
		hitIndex = Math.max(0, this.hitChunks.indexOf(selectedChunk));

		getDialogPane().lookupButton(ButtonType.APPLY).addEventFilter(ActionEvent.ACTION, e -> {

			// applying writes the chunk to disk, it must never close the dialog. the event is
			// consumed up front so that even a failing save leaves the editor open to retry.
			e.consume();

			Timer t = new Timer();

			DataProperty<Exception> exception = new DataProperty<>();

			new ProgressDialog(Translation.DIALOG_PROGRESS_TITLE_SAVING_CHUNK, getDialogPane().getScene().getWindow()).showProgressBar(r -> {
				try {
					r.setMax(4);

					r.updateProgress("region/" + FileHelper.createMCAFileName(selectedChunk.chunkToRegion()), 1);
					writeSingleChunk(new RegionMCAFile(FileHelper.createRegionMCAFilePath(selectedChunk.chunkToRegion())), new RegionChunk(selectedChunk), regionData);

					r.incrementProgress("poi/" + FileHelper.createMCAFileName(selectedChunk.chunkToRegion()));
					writeSingleChunk(new PoiMCAFile(FileHelper.createPoiMCAFilePath(selectedChunk.chunkToRegion())), new PoiChunk(selectedChunk), poiData);

					r.incrementProgress("entities/" + FileHelper.createMCAFileName(selectedChunk.chunkToRegion()));
					writeSingleChunk(new EntitiesMCAFile(FileHelper.createEntitiesMCAFilePath(selectedChunk.chunkToRegion())), new EntitiesChunk(selectedChunk), entitiesData);
				} catch (Exception ex) {
					exception.set(ex);
					LOGGER.warn("failed to save chunk", ex);
				} finally {
					r.done("");
				}
			});

			LOGGER.warn("took {} to save chunk {}", t, selectedChunk);

			CacheHelper.clearSelectionCache(tileMap);

			if (exception.get() == null) {
				// the applied edit is on disk now, so walking to another chunk must not ask about it
				refreshSavedState();
			}

			if (exception.get() != null) {
				new ErrorDialog(primaryStage, exception.get());
			}
		});

		buildChunkNav();
		buildEditorTabs();

		VBox content = new VBox(chunkNav, editors);
		VBox.setVgrow(editors, Priority.ALWAYS);
		getDialogPane().setContent(content);

		setResizable(true);

		setOnCloseRequest(e -> initPersistentLocationOnClose(this));
		initPersistentLocationOnOpen(this);

		Platform.runLater(editors::requestFocus);
	}

	/**
	 * Header line above the tabs with the arrows that walk the chunks a search matched, plus a list
	 * of every hit so a far away chunk does not have to be clicked through. It only shows up when
	 * there is more than one chunk to walk, so the plain chunk editor looks unchanged.
	 */
	private void buildChunkNav() {
		Button previous = new Button("\u25C0");
		previous.setTooltip(new Tooltip(Translation.DIALOG_NBT_EDITOR_CHUNK_PREV.toString()));
		previous.setOnAction(e -> goToHit(-1));
		Button next = new Button("\u25B6");
		next.setTooltip(new Tooltip(Translation.DIALOG_NBT_EDITOR_CHUNK_NEXT.toString()));
		next.setOnAction(e -> goToHit(1));
		chunkPositionLabel.getStyleClass().add("nbt-editor-chunk-position");
		chunkNav = new HBox(previous, chunkPositionLabel, chunkPicker(), next);
		chunkNav.setAlignment(Pos.CENTER_LEFT);
		chunkNav.getStyleClass().add("nbt-editor-chunk-nav");
		updateChunkPosition();
	}

	/** Drop down with every chunk the search matched, so the user can jump instead of stepping. */
	private ComboBox<Point2i> chunkPicker() {
		picker = new ComboBox<>(FXCollections.observableArrayList(hitChunks));
		picker.setConverter(new StringConverter<>() {
			@Override
			public String toString(Point2i chunk) {
				return chunk == null ? "" : chunk.getX() + ", " + chunk.getZ();
			}

			@Override
			public Point2i fromString(String string) {
				return null;
			}
		});
		picker.valueProperty().addListener((obs, before, after) -> {
			// updateChunkPosition sets the value itself when the chunk changes, that must not
			// trigger a second load
			if (after == null || syncingPicker || after.equals(selectedChunk)) {
				return;
			}
			if (hasUnsavedChanges() && !confirmDiscardChanges()) {
				picker.setValue(selectedChunk);
				return;
			}
			hitIndex = Math.max(0, hitChunks.indexOf(after));
			showChunk(after);
		});
		picker.setTooltip(new Tooltip(Translation.DIALOG_NBT_EDITOR_CHUNK_LIST.toString()));
		return picker;
	}

	private void updateChunkPosition() {
		boolean multiple = hitChunks.size() > 1;
		chunkNav.setVisible(multiple);
		chunkNav.setManaged(multiple);
		if (multiple) {
			chunkPositionLabel.setText(String.format(Translation.DIALOG_NBT_EDITOR_CHUNK_POSITION.toString(),
					selectedChunk.getX() + ", " + selectedChunk.getZ(), hitIndex + 1, hitChunks.size()));
		}
		// also kept in sync while the header is hidden, so the picker never points at another chunk
		// than the one on screen
		syncingPicker = true;
		try {
			picker.setValue(selectedChunk);
		} finally {
			syncingPicker = false;
		}
	}

	/** Steps through the hit list and wraps around, so the last hit leads back to the first. */
	public static int stepHitIndex(int current, int size, int delta) {
		if (size <= 0) {
			return 0;
		}
		return Math.floorMod(current + delta, size);
	}

	/**
	 * Walks to the previous or next chunk of the search result and loads it into the open dialog.
	 * Leaving a chunk with edits in it asks first, the same way closing a text editor with unsaved
	 * changes does.
	 */
	private void goToHit(int delta) {
		if (hitChunks.size() < 2) {
			return;
		}
		if (hasUnsavedChanges() && !confirmDiscardChanges()) {
			return;
		}
		hitIndex = stepHitIndex(hitIndex, hitChunks.size(), delta);
		showChunk(hitChunks.get(hitIndex));
	}

	/** Reloads the three tabs for another chunk, keeping the dialog and the query it was opened with. */
	private void showChunk(Point2i chunk) {
		selectedChunk = chunk;
		synchronized (loadLock) {
			loadGeneration++;
			tabsByRootType.clear();
			findBarsByRootType.clear();
			hitsByRootType.clear();
			loadedDataByRootType.clear();
			loadedTabs = 0;
			tabResolved = false;
			regionData = null;
			poiData = null;
			entitiesData = null;
		}
		getDialogPane().lookupButton(ButtonType.APPLY).setDisable(true);
		buildEditorTabs();
		updateChunkPosition();
	}

	private void buildEditorTabs() {
		Tab regionTab = createEditorTab("region", NbtQuery.ROOT_REGION, primaryStage, new RegionMCAFile(FileHelper.createRegionMCAFilePath(selectedChunk.chunkToRegion())), d -> regionData = d);
		Tab poiTab = createEditorTab("poi", NbtQuery.ROOT_POI, primaryStage, new PoiMCAFile(FileHelper.createPoiMCAFilePath(selectedChunk.chunkToRegion())), d -> poiData = d);
		Tab entitiesTab = createEditorTab("entities", NbtQuery.ROOT_ENTITIES, primaryStage, new EntitiesMCAFile(FileHelper.createEntitiesMCAFilePath(selectedChunk.chunkToRegion())), d -> entitiesData = d);
		editors.getTabs().setAll(regionTab, poiTab, entitiesTab);
	}

	/** True when the tree was edited after loading, i.e. switching the chunk would lose data. */
	boolean hasUnsavedChanges() {
		for (int rootType : new int[]{NbtQuery.ROOT_REGION, NbtQuery.ROOT_POI, NbtQuery.ROOT_ENTITIES}) {
			CompoundTag before = loadedDataByRootType.get(rootType);
			if (before == null) {
				continue;
			}
			CompoundTag now = dataFor(rootType);
			if (now == null || !before.equals(now)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Takes a new snapshot of the loaded data after it was written to disk, so an applied edit counts
	 * as saved and walking to the next chunk no longer warns about losing it.
	 */
	void refreshSavedState() {
		synchronized (loadLock) {
			for (int rootType : new int[]{NbtQuery.ROOT_REGION, NbtQuery.ROOT_POI, NbtQuery.ROOT_ENTITIES}) {
				CompoundTag now = dataFor(rootType);
				if (now != null) {
					loadedDataByRootType.put(rootType, now.copy());
				}
			}
		}
	}

	private CompoundTag dataFor(int rootType) {
		if (rootType == NbtQuery.ROOT_POI) {
			return poiData;
		}
		if (rootType == NbtQuery.ROOT_ENTITIES) {
			return entitiesData;
		}
		return regionData;
	}

	private boolean confirmDiscardChanges() {
		ButtonType discard = new ButtonType(Translation.DIALOG_NBT_EDITOR_DISCARD_CHANGES.toString(), ButtonBar.ButtonData.OK_DONE);
		ButtonType keep = new ButtonType(Translation.DIALOG_NBT_EDITOR_STAY.toString(), ButtonBar.ButtonData.CANCEL_CLOSE);
		Alert alert = new Alert(Alert.AlertType.CONFIRMATION,
				Translation.DIALOG_NBT_EDITOR_UNSAVED_CHANGES.toString(), discard, keep);
		alert.initOwner(getDialogPane().getScene().getWindow());
		alert.setTitle(Translation.DIALOG_EDIT_NBT_TITLE.toString());
		alert.setHeaderText(null);
		return alert.showAndWait().orElse(keep) == discard;
	}

	private Point2i getSelectedChunk(TileMap tileMap) {
		Selection selection = tileMap.getSelection();
		Point2i single = getSingleChunk(selection);
		if (single != null) {
			return single;
		}
		// a find selects every matching chunk, so "edit chunk" cannot take the selection: it opens
		// the chunk under the mouse instead of refusing to open at all
		Point2i hovered = tileMap.getHoveredBlock();
		if (hovered != null) {
			return hovered.blockToChunk();
		}
		throw new RuntimeException("no chunk selected or hovered to edit");
	}

	private static Point2i getSingleChunk(Selection selection) {
		if (selection == null || selection.size() != 1) {
			return null;
		}
		for (Long2ObjectMap.Entry<ChunkSet> entry : selection) {
			if (entry.getValue() == null || entry.getValue().size() != 1) {
				return null;
			}
			for (int p : entry.getValue()) {
				return new Point2i(p).add(new Point2i(entry.getLongKey()).regionToChunk());
			}
		}
		return null;
	}

	private <T extends Chunk> Tab createEditorTab(String title, int rootType, Stage primaryStage, MCAFile<T> mcaFile, Consumer<CompoundTag> consumer) {
		NBTTreeView nbtTreeView = new NBTTreeView();
		nbtTreeView.setArrayEditor(a -> new EditArrayDialog(a, primaryStage).showAndWait());

		BorderPane treeViewHolder = new BorderPane();
		treeViewHolder.getStyleClass().add("nbt-tree-view-holder");
		treeViewHolder.setCenter(treeViewPlaceHolder);
		// deleting and adding tags is the same row of buttons the data file editor uses, so a chunk
		// and a data file are edited with exactly the same gestures
		NBTEditorOptionsBar options = new NBTEditorOptionsBar(nbtTreeView, primaryStage, treeViewHolder, consumer);

		// the find bar sits directly above the tree, so finding and navigating reads exactly like a
		// text editor: query on top, the (now highlighted and scrolled) tree below it
		NBTFindBar findBar = new NBTFindBar(nbtTreeView);

		VBox box = new VBox();
		VBox.setVgrow(treeViewHolder, Priority.ALWAYS);
		box.getChildren().addAll(findBar, treeViewHolder, options);

		Tab tab = new Tab(title, box);
		tab.setClosable(false);

		// the query is not handed to the tab right away: which tab holds the hits is only known once
		// all three chunks finished loading, so the bar is filled when that tab is brought to front
		findBarsByRootType.put(rootType, findBar);
		tabsByRootType.put(rootType, tab);

		readSingleChunkAsync(rootType, mcaFile, nbtTreeView, treeViewHolder, options, consumer);

		return tab;
	}

	/**
	 * Counts the hits a freshly loaded tab would show for the prefill query and reports to the
	 * coordinator. Called from the loader thread, also when loading failed. Reports of a chunk the
	 * dialog has already walked away from are dropped.
	 */
	private void reportLoadedTab(int rootType, CompoundTag data, int generation) {
		int hits = countHits(rootType, data, prefill);
		Integer target = null;
		synchronized (loadLock) {
			if (generation != loadGeneration) {
				return;
			}
			// keep a copy of the data as it was loaded, so walking to the next chunk can tell an
			// untouched chunk from one the user has edited
			if (data != null) {
				loadedDataByRootType.put(rootType, data.copy());
			}
			hitsByRootType.put(rootType, hits);
			loadedTabs++;
			if (prefill != null && !tabResolved && loadedTabs >= 3) {
				tabResolved = true;
				target = pickTabWithHits();
			}
		}
		if (target != null) {
			int root = target;
			Platform.runLater(() -> {
				if (generation == loadGeneration) {
					showTabWithHits(root);
				}
			});
		}
	}

	/**
	 * Number of hits a single tab would show for a query, using the very same query the find bar
	 * searches the tree with. 0 for a tab the query does not apply to or that failed to load.
	 */
	public static int countHits(int rootType, CompoundTag data, NbtQuery query) {
		if (query == null || data == null || !query.matchesRoot(rootType)) {
			return 0;
		}
		NbtQuery treeQuery = NBTFindBar.toTreeQuery(query);
		if (treeQuery == null) {
			return 0;
		}
		return NbtSearcher.search(data, rootTypeName(rootType), treeQuery, NbtQuery.DEFAULT_LIMIT).getTotal();
	}

	/** region is kept when it holds hits, otherwise the first tab that does is taken. */
	private int pickTabWithHits() {
		return pickTabWithHits(hitsByRootType);
	}

	/**
	 * Decides which tab to show for the given hit counts, region first, then poi, then entities.
	 * Falls back to region when the search matches nothing at all, which is what the editor always
	 * did before and keeps "0 matches" visible instead of jumping to an arbitrary tab.
	 */
	public static int pickTabWithHits(Map<Integer, Integer> hits) {
		for (int rootType : new int[]{NbtQuery.ROOT_REGION, NbtQuery.ROOT_POI, NbtQuery.ROOT_ENTITIES}) {
			Integer count = hits.get(rootType);
			if (count != null && count > 0) {
				return rootType;
			}
		}
		return NbtQuery.ROOT_REGION;
	}

	/**
	 * Brings the tab holding the hits to front and runs "find all" in it, so the open editor shows
	 * the matches of the search that opened it instead of an empty region tree.
	 */
	private void showTabWithHits(int rootType) {
		Tab tab = tabsByRootType.get(rootType);
		if (tab != null) {
			editors.getSelectionModel().select(tab);
		}
		NBTFindBar findBar = findBarsByRootType.get(rootType);
		if (findBar != null && prefill != null) {
			findBar.applyQuery(prefill);
		}
	}

	private static String rootTypeName(int rootType) {
		if (rootType == NbtQuery.ROOT_POI) {
			return "poi";
		}
		if (rootType == NbtQuery.ROOT_ENTITIES) {
			return "entities";
		}
		return "region";
	}

	private <T extends Chunk> void readSingleChunkAsync(int rootType, MCAFile<T> mcaFile, NBTTreeView treeView, BorderPane treeViewHolder, NBTEditorOptionsBar options, Consumer<CompoundTag> consumer) {
		// the chunk shown right now is remembered here: if the user walks on to the next hit while
		// this tab is still loading, the result belongs to a chunk that is no longer on screen
		Point2i chunk = selectedChunk;
		int generation = loadGeneration;
		new Thread(() -> {
			CompoundTag loaded = null;
			try {
				LOGGER.debug("attempting to read single chunk from file: {}", chunk);
				if (mcaFile.getFile().exists()) {
					T chunkData = mcaFile.loadSingleChunk(chunk);
					if (chunkData == null || chunkData.getData() == null) {
						LOGGER.debug("no chunk data found for: {}", chunk);
						if (generation != loadGeneration) {
							return;
						}
						options.enableAddTagLabels(COMPOUND);
						Platform.runLater(() -> treeViewHolder.setCenter(UIFactory.label(Translation.DIALOG_EDIT_NBT_PLACEHOLDER_NO_CHUNK_DATA)));
						return;
					}
					loaded = chunkData.getData();
					final CompoundTag data = loaded;
					if (generation != loadGeneration) {
						return;
					}
					consumer.accept(data);
					Platform.runLater(() -> {
						if (generation != loadGeneration) {
							return;
						}
						treeView.load(data);
						treeViewHolder.setCenter(treeView);
						treeView.getRoot().setExpanded(true);
						getDialogPane().lookupButton(ButtonType.APPLY).setDisable(false);
					});
				} else {
					if (generation != loadGeneration) {
						return;
					}
					options.enableAddTagLabels(COMPOUND);
					Platform.runLater(() -> treeViewHolder.setCenter(UIFactory.label(Translation.DIALOG_EDIT_NBT_PLACEHOLDER_NO_REGION_FILE)));
				}
			} catch (IOException ex) {
				LOGGER.warn("failed to load chunk from file {}", mcaFile.getFile(), ex);
			} finally {
				// every tab has to report back, otherwise a tab without data would keep the editor
				// from ever deciding which tab to show
				reportLoadedTab(rootType, loaded, generation);
			}
		}).start();
	}

	private <T extends Chunk> void writeSingleChunk(MCAFile<T> mcaFile, T chunk, CompoundTag chunkData) throws IOException {
		if (chunkData != null) {
			chunk.setData(chunkData);
			chunk.setCompressionType(CompressionType.ZLIB);
		} else {
			chunk = null;
		}

		try {
			mcaFile.saveSingleChunk(selectedChunk, chunk);
			LOGGER.debug("saved single chunk to {}", mcaFile.getFile());
		} catch (IOException ex) {
			LOGGER.warn("failed to save single chunk to {}", mcaFile.getFile(), ex);
			throw ex;
		}
	}

	public static class Result {

		private final CompoundTag regionData, poiData, entitiesData;

		private Result(CompoundTag regionData, CompoundTag poiData, CompoundTag entitiesData) {
			this.regionData = regionData;
			this.poiData = poiData;
			this.entitiesData = entitiesData;
		}

		public CompoundTag getRegionData() {
			return regionData;
		}

		public CompoundTag getPoiData() {
			return poiData;
		}

		public CompoundTag getEntitiesData() {
			return entitiesData;
		}
	}
}
