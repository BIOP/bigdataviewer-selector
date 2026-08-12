/*-
 * #%L
 * Selector Behaviours for BigDataViewer
 * %%
 * Copyright (C) 2020 - 2026 BIOP (BioImaging & Optics Platform), EPFL
 * %%
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 * #L%
 */

package ch.epfl.biop.bdv.select;

import bdv.KeyConfigContexts;
import bdv.KeyConfigScopes;
import bdv.util.BdvFunctions;
import bdv.util.BdvHandle;
import bdv.util.BdvOptions;
import bdv.util.BdvOverlaySource;
import bdv.viewer.SourceAndConverter;
import bdv.viewer.SynchronizedViewerState;
import bdv.viewer.ViewerPanel;
import bdv.viewer.ViewerStateChange;
import bdv.viewer.ViewerStateChangeListener;
import org.scijava.plugin.Plugin;
import org.scijava.ui.behaviour.*;
import org.scijava.ui.behaviour.io.InputTriggerConfig;
import org.scijava.ui.behaviour.io.gui.CommandDescriptionProvider;
import org.scijava.ui.behaviour.io.gui.CommandDescriptions;
import org.scijava.ui.behaviour.util.Behaviours;
import org.scijava.ui.behaviour.util.TriggerBehaviourBindings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import javax.swing.InputMap;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import static bdv.viewer.ViewerStateChange.*;

/**
 * Adds a selection sources mode in a {@link BdvHandle} The selection mode can
 * be toggled programmatically or using a key bindings defined in the
 * constructor of this class It is used in conjunction with a BdvOverlay layer
 * {@link SourceSelectorOverlay} which can be retrieved with
 * {@link SourceSelectorBehaviour#getSourceSelectorOverlay()} The selections can
 * be triggered by GUI actions in the linked {@link SourceSelectorOverlay} or
 * directly programmatically The selected sources are unordered It is designed
 * such as only visible sources can be selected - if source becomes invisible,
 * then they are removed from the current selected. This is made to avoid that a
 * removed Source is kept in memory while it is invisible or more importantly if
 * it is removed from the BdvHandle. Limitation : supports 'Box' (3d RAI with an
 * Affine Transform) sources - A WarpedSource will not be handled well without
 * some extra effort for instance See BdvSelectorDemo for usage example
 *
 * @author Nicolas Chiaruttini, BIOP, EPFL, 2020
 */

public class SourceSelectorBehaviour implements ViewerStateChangeListener {

	protected static final Logger logger = LoggerFactory.getLogger(
		SourceSelectorBehaviour.class);

	final public static String SOURCES_SELECTOR_MAP = "sources-selector";
	final public static String SOURCES_SELECTOR_TOGGLE_MAP =
		"source-selector-toggle";

	final public static String SET = "SET";
	final public static String ADD = "ADD";
	final public static String REMOVE = "REMOVE";

	// Command names and their default triggers. The names are the keys used in
	// the {@link InputTriggerConfig}, i.e. what a user edits in the BDV
	// preferences dialog (Keymap page) or in a keymap YAML file. The default
	// triggers are only used when the config has no entry for the command.
	final public static String SELECT_SET_SOURCES = "select-set-sources";
	final public static String SELECT_ADD_SOURCES = "select-add-sources";
	final public static String SELECT_REMOVE_SOURCES = "select-remove-sources";
	final public static String SELECT_ALL_VISIBLE_SOURCES =
		"select-all-visible-sources";

	final public static String[] SOURCES_SELECTOR_TOGGLE_KEYS = new String[] {
		"E" };
	final public static String[] SELECT_SET_SOURCES_KEYS = new String[] {
		"button1" };
	final public static String[] SELECT_ADD_SOURCES_KEYS = new String[] {
		"shift button1" };
	final public static String[] SELECT_REMOVE_SOURCES_KEYS = new String[] {
		"ctrl button1" };
	final public static String[] SELECT_ALL_VISIBLE_SOURCES_KEYS = new String[] {
		"ctrl A" };

	final SourceSelectorOverlay selectorOverlay;

	BdvOverlaySource<?> bos;

	final BdvHandle bdvh;

	final TriggerBehaviourBindings triggerbindings;

	final ViewerPanel viewer;

	final Behaviours behaviours;

	final Behaviours behavioursToggle;

	boolean isInstalled; // flag for the toggle action

	// Listeners list
	final List<ToggleListener> toggleListeners = new ArrayList<>();

	final List<SelectedSourcesListener> selectedSourceListeners =
		new ArrayList<>();

	protected final Set<SourceAndConverter<?>> selectedSources = ConcurrentHashMap
		.newKeySet(); // Makes a concurrent set

	/**
	 * Construct a SourceSelectorBehaviour with its default, non configurable
	 * triggers.
	 *
	 * @param bdvh BdvHandle associated to this behaviour
	 * @param triggerToggleSelector Trigger Input Toggle which will activate /
	 *          deactivate this mode
	 */
	public SourceSelectorBehaviour(BdvHandle bdvh, String triggerToggleSelector) {
		this(bdvh, new InputTriggerConfig(), triggerToggleSelector);
	}

	/**
	 * Construct a SourceSelectorBehaviour whose triggers can be overridden by the
	 * user.
	 * <p>
	 * Triggers found in {@code config} for the {@code bdv} context take
	 * precedence over the defaults hardcoded here and over
	 * {@code triggerToggleSelector}. Pass the {@link InputTriggerConfig} of the
	 * BDV window's {@link bdv.ui.keymap.Keymap} to make the selection bindings
	 * editable in the BDV preferences dialog, and call
	 * {@link #updateKeyConfig(InputTriggerConfig)} whenever that keymap changes.
	 *
	 * @param bdvh BdvHandle associated to this behaviour
	 * @param config key config which can override the default triggers
	 * @param triggerToggleSelector default trigger which will activate /
	 *          deactivate this mode, used if {@code config} does not define
	 *          {@link #SOURCES_SELECTOR_TOGGLE_MAP}
	 */
	public SourceSelectorBehaviour(BdvHandle bdvh, InputTriggerConfig config,
		String triggerToggleSelector)
	{
		this.bdvh = bdvh;
		this.triggerbindings = bdvh.getTriggerbindings();
		this.viewer = bdvh.getViewerPanel();

		selectorOverlay = new SourceSelectorOverlay(viewer, this);

		ClickBehaviour toggleSelector = (x, y) -> {
			if (isInstalled) {
				uninstall();
			}
			else {
				install();
			}
		};

		behavioursToggle = new Behaviours(config,
			KeyConfigContexts.BIGDATAVIEWER);
		behavioursToggle.behaviour(toggleSelector, SOURCES_SELECTOR_TOGGLE_MAP,
			triggerToggleSelector);
		behavioursToggle.install(bdvh.getTriggerbindings(),
			SOURCES_SELECTOR_TOGGLE_MAP);
		behaviours = new Behaviours(config, KeyConfigContexts.BIGDATAVIEWER);

		viewer.state().changeListeners().add(this);
	}

	/**
	 * Re-reads all triggers of this selector from {@code config}. Call this when
	 * the user has edited the keymap, typically from a
	 * {@link bdv.ui.keymap.Keymap.UpdateListener}. The bindings are updated in
	 * place, so this works whether the selection mode is currently enabled or
	 * not.
	 *
	 * @param config the new key config
	 */
	public void updateKeyConfig(InputTriggerConfig config) {
		behavioursToggle.updateKeyConfig(config);
		behaviours.updateKeyConfig(config);
	}

	/**
	 * Gets the overlay layer associated with the source selector
	 * 
	 * @return the selector overlay
	 */
	public SourceSelectorOverlay getSourceSelectorOverlay() {
		return selectorOverlay;
	}

	/**
	 * @return the BdhHandle associated to this Selector
	 */
	public BdvHandle getBdvHandle() {
		return bdvh;
	}

	/**
	 * Activate the selection mode
	 */
	public synchronized void enable() {
		if (!isInstalled) {
			install();
		}
	}

	/**
	 * Deactivate the selection mode
	 */
	public synchronized void disable() {
		if (isInstalled) {
			uninstall();
		}
	}

	public synchronized boolean isEnabled() {
		return isInstalled;
	}

	/**
	 * Completely disassociate the selector with this BdvHandle TODO safe in terms
	 * of freeing memory ?
	 */
	public void remove() {
		disable();
		triggerbindings.removeInputTriggerMap(SOURCES_SELECTOR_TOGGLE_MAP);
		triggerbindings.removeBehaviourMap(SOURCES_SELECTOR_TOGGLE_MAP);
		// Unregister from the viewer state, otherwise this instance keeps
		// receiving events and cannot be garbage collected (see constructor)
		viewer.state().changeListeners().remove(this);
	}

	/**
	 * Private : call enable instead
	 */
	synchronized void install() {
		isInstalled = true;
		selectorOverlay.addSelectionBehaviours(behaviours);
		triggerbindings.addBehaviourMap(SOURCES_SELECTOR_MAP, behaviours
			.getBehaviourMap());
		triggerbindings.addInputTriggerMap(SOURCES_SELECTOR_MAP, behaviours
			.getInputTriggerMap(), "transform", "bdv");
		int nTimePoints = bdvh.getViewerPanel().state().getNumTimepoints();
		int currentTimePoint = bdvh.getViewerPanel().state().getCurrentTimepoint();
		bos = BdvFunctions.showOverlay(selectorOverlay, "Selector_Overlay",
			BdvOptions.options().addTo(bdvh));

		// Add it to all the groups... otherwise the overlay does not show when you're in group mode
		SourceAndConverter<Void> overlaySource = getSourceAndConverterFrom(bos);
		bdvh.getViewerPanel().state().getGroups().forEach(group -> bdvh
			.getViewerPanel().state().addSourcesToGroup(Collections.singleton(
				overlaySource), group));

		bdvh.getViewerPanel().state().setNumTimepoints(nTimePoints);
		bdvh.getViewerPanel().state().setCurrentTimepoint(currentTimePoint);
		bdvh.getKeybindings().addInputMap("blocking-source-selector",
			new InputMap(), "bdv", "navigation");
		toggleListeners.forEach(ToggleListener::isEnabled);
	}

	static SourceAndConverter<Void> getSourceAndConverterFrom(BdvOverlaySource<?> overlay) {
		try {
			Field sourceField = BdvOverlaySource.class.getDeclaredField("source");
			sourceField.setAccessible(true);
			return (SourceAndConverter<Void>) sourceField.get(overlay);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	public void addBehaviour(Behaviour behaviour, String behaviourName,
		String[] triggers)
	{
		behaviours.behaviour(behaviour, behaviourName, triggers);
	}

	/**
	 * Private : call disable instead
	 */
	synchronized void uninstall() {
		isInstalled = false;
		// Removing the overlay source resets the number of timepoints, because
		// vistools recomputes it over its own BdvSources and ignores the core
		// SourceAndConverters (see bigdataviewer-playground#272). Save and restore
		// the timepoints manually around the removal. Source group membership is
		// no longer corrupted since bigdataviewer-core#177 was fixed, so the
		// overlay is simply removed from all groups by removeFromBdv().
		SynchronizedViewerState state = bdvh.getViewerPanel().state();
		int nTimePoints = state.getNumTimepoints();
		int currentTimePoint = state.getCurrentTimepoint();
		bos.removeFromBdv();
		state.setNumTimepoints(nTimePoints);
		state.setCurrentTimepoint(currentTimePoint);
		triggerbindings.removeBehaviourMap(SOURCES_SELECTOR_MAP);
		triggerbindings.removeInputTriggerMap(SOURCES_SELECTOR_MAP);
		bdvh.getKeybindings().removeInputMap("blocking-source-selector");
		toggleListeners.forEach(ToggleListener::isDisabled);
	}

	// API to Control Selected Sources

	/**
	 * @return current selected source
	 */
	public Set<SourceAndConverter<?>> getSelectedSources() {
		synchronized (selectedSources) {
			return new HashSet<>(selectedSources);
		}
	}

	private Set<SourceAndConverter<?>> removeOverlaySources(
		Set<SourceAndConverter<?>> in)
	{
		// HACK TODO : better filtering
		return in.stream().filter(sac -> sac.getSpimSource().getSource(viewer
			.state().getCurrentTimepoint(), 0) != null).collect(Collectors.toSet());
	}

	/**
	 * Main function coordinating events : it is called by all the other functions
	 * to process the modifications of the selected sources
	 * 
	 * @param currentSources set of sources involved in the current modification
	 * @param mode see SET ADD REMOVE
	 * @param eventSource a String which can indicate the origin of the
	 *          modification
	 */
	public void processSelectionModificationEvent(
		Set<SourceAndConverter<?>> currentSources, String mode, String eventSource)
	{
		synchronized (selectedSources) {
			int initialSize = selectedSources.size();
			switch (mode) {
				case SET:
					// Sanity check : only visible sources can be selected
					if (currentSources.stream().anyMatch(sac -> !viewer.state()
						.isSourceVisible(sac)))
					{
						logger.error(
							"Attempt to select a source which is not visible - selection ignored");
						return;
					}
					selectedSources.clear();
					selectedSources.addAll(removeOverlaySources(currentSources));
					break;
				case SourceSelectorBehaviour.ADD:
					// Sanity check : only visible sources can be selected
					if (currentSources.stream().anyMatch(sac -> !viewer.state()
						.isSourceVisible(sac)))
					{
						logger.error(
							"Attempt to select a source which is not visible - selection ignored");
						return;
					}
					selectedSources.addAll(removeOverlaySources(currentSources));
					break;
				case SourceSelectorBehaviour.REMOVE:
					selectedSources.removeAll(currentSources);
					break;
				default:
					logger.error("Unhandled {} selected source modification event",
						mode);
					break;
			}

			if (!currentSources.isEmpty()) {
				Set<SourceAndConverter<?>> lastSelected = removeOverlaySources(
					currentSources);
				selectedSourceListeners.forEach(listener -> {
					listener.selectedSourcesUpdated(getSelectedSources(), eventSource);
					listener.lastSelectionEvent(lastSelected, mode, eventSource);
				});
			}

			if ((currentSources.isEmpty()) && (initialSize != 0) && (mode.equals(
				SET)))
			{
				selectedSourceListeners.forEach(listener -> {
					listener.selectedSourcesUpdated(getSelectedSources(), eventSource);
					listener.lastSelectionEvent(currentSources, mode, eventSource);
				});
			}

			viewer.requestRepaint();
		}
	}

	public void selectedSourcesClear(String eventSource) {
		processSelectionModificationEvent(new HashSet<>(), SET, eventSource);
	}

	public void selectedSourceAdd(Collection<SourceAndConverter<?>> sources,
		String eventSource)
	{
		Set<SourceAndConverter<?>> sourcesSet = new HashSet<>(sources);
		processSelectionModificationEvent(sourcesSet, ADD, eventSource);
	}

	public void selectedSourceRemove(Collection<SourceAndConverter<?>> sources,
		String eventSource)
	{
		Set<SourceAndConverter<?>> sourcesSet = new HashSet<>(sources);
		processSelectionModificationEvent(sourcesSet, REMOVE, eventSource);
	}

	// For convenience : single source methods
	public void selectedSourcesClear() {
		selectedSourcesClear("API");
	}

	public void selectedSourceRemove(Collection<SourceAndConverter<?>> sources) {
		selectedSourceRemove(sources, "API");
	}

	public void selectedSourceAdd(Collection<SourceAndConverter<?>> sources) {
		selectedSourceAdd(sources, "API");
	}

	public void selectedSourceRemove(SourceAndConverter<?> source) {
		Set<SourceAndConverter<?>> sourcesSet = new HashSet<>();
		sourcesSet.add(source);
		selectedSourceRemove(sourcesSet, "API");
	}

	public void selectedSourceAdd(SourceAndConverter<?> source) {
		Set<SourceAndConverter<?>> sourcesSet = new HashSet<>();
		sourcesSet.add(source);
		selectedSourceAdd(sourcesSet, "API");
	}

	// Handle sources being removed programmatically are becoming invisible ->
	// cleans selected sources, if necessary
	@Override
	public void viewerStateChanged(ViewerStateChange change) {
		if (change.equals(NUM_SOURCES_CHANGED) || change.equals(
			VISIBILITY_CHANGED))
		{
			selectorOverlay.updateBoxes();
			// Removes potentially selected source which has been removed from bdv
			Set<SourceAndConverter<?>> leftOvers = new HashSet<>(selectedSources);
			leftOvers.removeAll(viewer.state().getVisibleSources());
			// selectedSources.removeAll(leftOvers);
			if (!leftOvers.isEmpty()) {
				processSelectionModificationEvent(leftOvers, REMOVE, change.toString());
			}
		}
		if (change.equals(CURRENT_TIMEPOINT_CHANGED)) {
			// Only the boxes are recomputed : the selection is deliberately left
			// untouched when the user browses the timepoints. A source selected at a
			// timepoint where it exists stays selected at the timepoints where it
			// does not, so that a selection can be built up across the whole time
			// range. This works because the pruning above keys off
			// getVisibleSources(), which - contrary to getVisibleAndPresentSources()
			// - does not depend on the current timepoint, and because
			// setCurrentTimepoint only fires CURRENT_TIMEPOINT_CHANGED. Do not add
			// pruning here.
			selectorOverlay.updateBoxes();
		}
	}

	/**
	 * Adds a toggle listener see {@link ToggleListener}
	 * 
	 * @param toggleListener toggle listener
	 */
	public void addToggleListener(ToggleListener toggleListener) {
		toggleListeners.add(toggleListener);
	}

	/**
	 * Removes a toggle listener, {@link ToggleListener}
	 * 
	 * @param toggleListener toggle listener
	 */
	public void removeToggleListener(ToggleListener toggleListener) {
		toggleListeners.remove(toggleListener);
	}

	/**
	 * Adds a selected source listener, see {@link SelectedSourcesListener}
	 * 
	 * @param selectedSourcesListener selected sources listener
	 */
	public void addSelectedSourcesListener(
		SelectedSourcesListener selectedSourcesListener)
	{
		selectedSourceListeners.add(selectedSourcesListener);
	}

	/**
	 * Removes a selected source listener, see {@link SelectedSourcesListener}
	 * 
	 * @param selectedSourcesListener selected sources listener
	 */
	public void removeSelectedSourcesListener(
		SelectedSourcesListener selectedSourcesListener)
	{
		selectedSourceListeners.remove(selectedSourcesListener);
	}

	/**
	 * Makes the selector commands discoverable by the keymap editor of the BDV
	 * preferences dialog, so that they can be listed and re-bound next to the
	 * commands of BDV itself.
	 */
	@Plugin(type = CommandDescriptionProvider.class)
	public static class Descriptions extends CommandDescriptionProvider {

		public Descriptions() {
			super(KeyConfigScopes.BIGDATAVIEWER, KeyConfigContexts.BIGDATAVIEWER);
		}

		@Override
		public void getCommandDescriptions(final CommandDescriptions descriptions) {
			descriptions.add(SOURCES_SELECTOR_TOGGLE_MAP,
				SOURCES_SELECTOR_TOGGLE_KEYS,
				"Toggle between the source selection (editor) mode and the navigation mode.");
			descriptions.add(SELECT_SET_SOURCES, SELECT_SET_SOURCES_KEYS,
				"Select the sources within the dragged rectangle, discarding the previous selection.");
			descriptions.add(SELECT_ADD_SOURCES, SELECT_ADD_SOURCES_KEYS,
				"Add the sources within the dragged rectangle to the current selection.");
			descriptions.add(SELECT_REMOVE_SOURCES, SELECT_REMOVE_SOURCES_KEYS,
				"Remove the sources within the dragged rectangle from the current selection.");
			descriptions.add(SELECT_ALL_VISIBLE_SOURCES,
				SELECT_ALL_VISIBLE_SOURCES_KEYS,
				"Add all currently visible sources to the selection.");
		}
	}

}
