
package ch.epfl.biop.bdv.select;

/**
 * Interface to respond to source selection edition mode changes
 *
 * @author Nicolas Chiaruttini, BIOP, EPFL
 */

public interface ToggleListener {

	/**
	 * Triggered when the selection mode is enabled
	 */
	void isEnabled();

	/**
	 * Triggered when the selection mode is disabled
	 */
	void isDisabled();
}
