
package ch.epfl.biop.bdv.select;

import bdv.util.BdvFunctions;
import bdv.util.BdvHandle;
import bdv.util.BdvOptions;
import bdv.util.BdvOverlaySource;
import bdv.viewer.SourceAndConverter;
import bdv.viewer.SourceGroup;
import bdv.viewer.SynchronizedViewerState;
import bdv.viewer.ViewerPanel;
import bdv.viewer.ViewerState;
import bdv.viewer.ViewerStateChange;
import bdv.viewer.ViewerStateChangeListener;
import org.scijava.ui.behaviour.*;
import org.scijava.ui.behaviour.io.InputTriggerConfig;
import org.scijava.ui.behaviour.util.Behaviours;
import org.scijava.ui.behaviour.util.TriggerBehaviourBindings;
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

	final public static String SOURCES_SELECTOR_MAP = "sources-selector";
	final public static String SOURCES_SELECTOR_TOGGLE_MAP =
		"source-selector-toggle";

	final public static String SET = "SET";
	final public static String ADD = "ADD";
	final public static String REMOVE = "REMOVE";

	final SourceSelectorOverlay selectorOverlay;

	BdvOverlaySource<?> bos;

	final BdvHandle bdvh;

	final TriggerBehaviourBindings triggerbindings;

	final ViewerPanel viewer;

	final Behaviours behaviours;

	boolean isInstalled; // flag for the toggle action

	// Listeners list
	final List<ToggleListener> toggleListeners = new ArrayList<>();

	final List<SelectedSourcesListener> selectedSourceListeners =
		new ArrayList<>();

	protected final Set<SourceAndConverter<?>> selectedSources = ConcurrentHashMap
		.newKeySet(); // Makes a concurrent set

	/**
	 * Construct a SourceSelectorBehaviour
	 * 
	 * @param bdvh BdvHandle associated to this behaviour
	 * @param triggerToggleSelector Trigger Input Toggle which will activate /
	 *          deactivate this mode
	 */
	public SourceSelectorBehaviour(BdvHandle bdvh, String triggerToggleSelector) {
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

		Behaviours behavioursToggle = new Behaviours(new InputTriggerConfig(),
			"bdv");
		behavioursToggle.behaviour(toggleSelector, SOURCES_SELECTOR_TOGGLE_MAP,
			triggerToggleSelector);
		behavioursToggle.install(bdvh.getTriggerbindings(),
			"source-selector-toggle");
		behaviours = new Behaviours(new InputTriggerConfig(), "bdv");

		viewer.state().changeListeners().add(this);
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
	}

	final Set<SourceGroup> sourceGroups = new HashSet<>();

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
		bdvh.getViewerPanel().state().getGroups().forEach(
				group -> {
						bdvh.getViewerPanel().state().addSourcesToGroup(Collections.singleton(getSourceAndConverterFrom(bos)), group);
						sourceGroups.add(group); // To remind us to remove the overlay when it's uninstalled
				}
		);

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
		// Removing the bos removes a lot of things from bdv - this needs to be manually restored (groups and timepoints)
		// Remove the overlay from all groups
		sourceGroups.forEach(
				group -> bdvh.getViewerPanel().state().removeSourceFromGroup(getSourceAndConverterFrom(bos), group)
		);
		sourceGroups.clear();

		SynchronizedViewerState state = bdvh.getViewerPanel().state();
		ViewerState snap = state.snapshot();
		bos.removeFromBdv();
		state.setNumTimepoints(snap.getNumTimepoints());
		state.setCurrentTimepoint(snap.getCurrentTimepoint());
		for (SourceGroup group: state.getGroups()) {
			state.addSourcesToGroup(snap.getSourcesInGroup(group), group);
			state.setGroupActive(group, snap.isGroupActive(group));
		}
		state.setCurrentGroup(snap.getCurrentGroup());
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
						System.err.println(
							"Error : attempt to select a source which is not visible - selection ignored");
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
						System.err.println(
							"Error : attempt to select a source which is not visible - selection ignored");
						return;
					}
					selectedSources.addAll(removeOverlaySources(currentSources));
					break;
				case SourceSelectorBehaviour.REMOVE:
					selectedSources.removeAll(currentSources);
					break;
				default:
					System.err.println("Unhandled " + mode +
						" selected source modification event");
					break;
			}

			if (!currentSources.isEmpty()) {
				selectedSourceListeners.forEach(listener -> {
					listener.selectedSourcesUpdated(getSelectedSources(), eventSource);
					listener.lastSelectionEvent(removeOverlaySources(currentSources),
						mode, eventSource);
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

}
