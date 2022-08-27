
package ch.epfl.biop.bdv.select;

import bdv.viewer.SourceAndConverter;
import java.util.Collection;
import java.util.Set;

/**
 * Interface to respond to source selection changes
 *
 * @author Nicolas Chiaruttini, BIOP, EPFL
 */

public interface SelectedSourcesListener {

	/**
	 * Function triggered first when the selected sources are changed
	 * 
	 * @param selectedSources : the new list of selected sources
	 * @param triggerMode : string which indicates the origin of the event that
	 *          triggered a change see
	 *          {@link SourceSelectorBehaviour#processSelectionModificationEvent(Set, String, String)}
	 */
	void selectedSourcesUpdated(Collection<SourceAndConverter<?>> selectedSources,
		String triggerMode);

	/**
	 * Function triggered second and which specifies what sources have had their
	 * status changed This makes it easier to follow the successive steps during
	 * consecutive selection events
	 * 
	 * @param lastSelectedSources : the list of sources which have been involved
	 *          in the last selection event
	 * @param mode : should be {@link SourceSelectorBehaviour#SET} or
	 *          {@link SourceSelectorBehaviour#ADD} or
	 *          {@link SourceSelectorBehaviour#REMOVE}
	 * @param triggerMode string which indicates the origin of the event that
	 *          triggered a change see
	 *          {@link SourceSelectorBehaviour#processSelectionModificationEvent(Set, String, String)}
	 */
	void lastSelectionEvent(Collection<SourceAndConverter<?>> lastSelectedSources,
		String mode, String triggerMode);

}
