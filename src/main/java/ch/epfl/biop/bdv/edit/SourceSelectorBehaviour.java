package ch.epfl.biop.bdv.edit;

import bdv.util.BdvFunctions;
import bdv.util.BdvHandle;
import bdv.util.BdvOptions;
import bdv.util.BdvOverlaySource;
import bdv.viewer.SourceAndConverter;
import bdv.viewer.ViewerPanel;
import bdv.viewer.ViewerStateChange;
import bdv.viewer.ViewerStateChangeListener;
import org.scijava.ui.behaviour.*;
import org.scijava.ui.behaviour.io.InputTriggerConfig;
import org.scijava.ui.behaviour.util.Behaviours;
import org.scijava.ui.behaviour.util.TriggerBehaviourBindings;

import javax.swing.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static bdv.viewer.ViewerStateChange.NUM_SOURCES_CHANGED;
import static bdv.viewer.ViewerStateChange.VISIBILITY_CHANGED;

public class SourceSelectorBehaviour implements ViewerStateChangeListener {

    SourceSelectorOverlay editorOverlay;

    final BdvHandle bdvh;

    private final TriggerBehaviourBindings triggerbindings;

    ViewerPanel viewer;

    private static final String SOURCES_SELECTOR_MAP = "sources-selector";

    private static final String SOURCES_SELECTOR_TOGGLE_MAP = "source-selector-toggle";

    private final Behaviours behaviours;

    BdvOverlaySource bos;

    boolean isInstalled; // flag for the toggle action

    List<ToggleListener> toggleListeners = new ArrayList<>();

    final public static String SET = "SET";
    final public static String ADD = "ADD";
    final public static String REMOVE = "REMOVE";

    List<SelectedSourcesListener> selectedSourceListeners = new ArrayList<>();

    protected Set<SourceAndConverter<?>> selectedSources = ConcurrentHashMap.newKeySet(); // To think ? Make a concurrent Keyset

    public SourceSelectorBehaviour(BdvHandle bdvh, String triggerToggleSelector) {
        this.bdvh = bdvh;
        this.triggerbindings = bdvh.getTriggerbindings();
        this.viewer = bdvh.getViewerPanel();

        editorOverlay = new SourceSelectorOverlay(viewer, this);

        ClickBehaviour toggleEditor = (x,y) -> {
            if (isInstalled) {
                uninstall();
            } else {
                install();
            }
        };

        Behaviours behavioursToggle = new Behaviours(new InputTriggerConfig(), "bdv");
        behavioursToggle.behaviour(toggleEditor, SOURCES_SELECTOR_TOGGLE_MAP, triggerToggleSelector);
        behavioursToggle.install(bdvh.getTriggerbindings(), "source-selector-toggle");
        behaviours = new Behaviours( new InputTriggerConfig(), "bdv" );

        viewer.state().changeListeners().add(this);
    }

    public SourceSelectorOverlay getSourceSelectorOverlay() {
        return editorOverlay;
    }

    public BdvHandle getBdvHandle() {
        return bdvh;
    }

    public synchronized void enable() {
        if (!isInstalled) {
            install();
        }
    }

    public synchronized void disable() {
        if (isInstalled) {
            uninstall();
        }
    }

    public void remove() {
        triggerbindings.removeInputTriggerMap("source-selector-toggle");
        triggerbindings.removeBehaviourMap("source-selector-toggle");
    }

    synchronized void install() {
        isInstalled = true;
        editorOverlay.addSelectionBehaviours(behaviours);
        triggerbindings.addBehaviourMap(SOURCES_SELECTOR_MAP, behaviours.getBehaviourMap());
        triggerbindings.addInputTriggerMap(SOURCES_SELECTOR_MAP, behaviours.getInputTriggerMap(), "transform", "bdv");
        bos = BdvFunctions.showOverlay(editorOverlay, "Editor_Overlay", BdvOptions.options().addTo(bdvh));
        bdvh.getKeybindings().addInputMap("blocking-source-selector", new InputMap(), "bdv", "navigation");
        toggleListeners.forEach(tl -> tl.enable());
    }

    synchronized void uninstall() {
        isInstalled = false;
        bos.removeFromBdv();
        triggerbindings.removeBehaviourMap( SOURCES_SELECTOR_MAP );
        triggerbindings.removeInputTriggerMap( SOURCES_SELECTOR_MAP );
        bdvh.getKeybindings().removeInputMap("blocking-source-selector");
        toggleListeners.forEach(tl -> tl.disable());
    }

    // API Control Selected Sources

    public Set<SourceAndConverter<?>> getSelectedSources() {
        synchronized (selectedSources) {
            HashSet<SourceAndConverter<?>> copySelectedSources = new HashSet<>();
            copySelectedSources.addAll(selectedSources);
            return copySelectedSources;
        }
    }

    public void processSelectionModificationEvent(Set<SourceAndConverter<?>> currentSources, String mode, String eventSource) {
        synchronized (selectedSources) {

            int initialSize = selectedSources.size();
            switch(mode) {
                case SET :
                    selectedSources.clear();
                    selectedSources.addAll(currentSources);
                    break;
                case SourceSelectorBehaviour.ADD :
                    // Sanity check : only visible sources can be selected
                    if (currentSources.stream().anyMatch(sac -> !bdvh.getViewerPanel().state().isSourceVisible(sac))) {
                        System.err.println("Error : attempt to select a source which is not visible - selection ignored");
                        return;
                    }
                    selectedSources.addAll(currentSources);
                    break;
                case SourceSelectorBehaviour.REMOVE :
                    selectedSources.removeAll(currentSources);
                    break;
                default:
                    System.err.println("Unhandled "+mode+" selected source modification event");
                    break;
            }

            if (currentSources.size()!=0) {
                selectedSourceListeners.forEach(listener -> {
                    listener.selectedSourcesUpdated(getSelectedSources(), eventSource);
                    listener.lastSelectionEvent(currentSources, mode, eventSource);
                });
            }

            if ((currentSources.size()==0)&&(initialSize!=0)&&(mode.equals(SET))) {
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

    public void selectedSourceAdd(Collection<SourceAndConverter<?>> sources, String eventSource) {
        Set<SourceAndConverter<?>> sourcesSet = new HashSet<>(sources);
        processSelectionModificationEvent(sourcesSet, ADD, eventSource);
    }

    public void selectedSourceRemove(Collection<SourceAndConverter<?>> sources, String eventSource) {
        Set<SourceAndConverter<?>> sourcesSet = new HashSet<>(sources);
        processSelectionModificationEvent(sourcesSet, REMOVE, eventSource);
    }

    // For convenience

    public void selectedSourcesClear() {
        selectedSourcesClear("API");
    }

    public void selectedSourceRemove(Collection<SourceAndConverter<?>> sources) {
        selectedSourceRemove(sources,"API");
    }

    public void selectedSourceAdd(Collection<SourceAndConverter<?>> sources) {
        selectedSourceAdd(sources, "API");
    }

    public void selectedSourceRemove(SourceAndConverter<?> source) {
        Set<SourceAndConverter<?>> sourcesSet = new HashSet<>();
        sourcesSet.add(source);
        selectedSourceRemove(sourcesSet,"API");
    }

    public void selectedSourceAdd(SourceAndConverter<?> source) {
        Set<SourceAndConverter<?>> sourcesSet = new HashSet<>();
        sourcesSet.add(source);
        selectedSourceAdd(sourcesSet, "API");
    }

    // Handles sources being removed programmatically are becoming invisible -> cleans selected sources, if necessary
    @Override
    public void viewerStateChanged(ViewerStateChange change) {
        if (change.equals(NUM_SOURCES_CHANGED)||change.equals(VISIBILITY_CHANGED)) {
            editorOverlay.updateBoxes();
            // Removes potentially selected source which has been removed from bdv
            Set<SourceAndConverter<?>> leftOvers = new HashSet<>();
            leftOvers.addAll(selectedSources);
            leftOvers.removeAll(viewer.state().getVisibleSources());
            selectedSources.removeAll(leftOvers);
            if (leftOvers.size()>0) {
                processSelectionModificationEvent(leftOvers, REMOVE, change.toString());
            }
        }
    }

    // Listeners

    public void addToggleListener(ToggleListener toggleListener) {
        toggleListeners.add(toggleListener);
    }

    public void removeToggleListener(ToggleListener toggleListener) {
        toggleListeners.remove(toggleListener);
    }

    public void addSelectedSourcesListener(SelectedSourcesListener selectedSourcesListener) {
        selectedSourceListeners.add(selectedSourcesListener);
    }

    public void removeSelectedSourcesListener(SelectedSourcesListener selectedSourcesListener) {
        selectedSourceListeners.remove(selectedSourcesListener);
    }

}
