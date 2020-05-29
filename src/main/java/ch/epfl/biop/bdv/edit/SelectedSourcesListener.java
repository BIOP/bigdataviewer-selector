package ch.epfl.biop.bdv.edit;

import bdv.viewer.SourceAndConverter;

import java.util.Collection;

public interface SelectedSourcesListener {

    void selectedSourcesUpdated(Collection<SourceAndConverter<?>> selectedSources, String triggerMode);

    void lastSelectionEvent(Collection<SourceAndConverter<?>> lastSelectedSources, String mode, String triggerMode);

}
