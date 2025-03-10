import bdv.util.BdvFunctions;
import bdv.util.BdvHandle;
import bdv.util.BdvOptions;
import bdv.util.BdvOverlay;
import bdv.util.BdvOverlaySource;
import bdv.util.BdvStackSource;
import bdv.viewer.SourceAndConverter;
import ch.epfl.biop.bdv.select.SelectedSourcesListener;
import ch.epfl.biop.bdv.select.SourceNameOverlay;
import ch.epfl.biop.bdv.select.SourceSelectorBehaviour;
import ch.epfl.biop.bdv.select.ToggleListener;
import ij.IJ;
import ij.ImagePlus;
import mpicbg.spim.data.SpimData;
import mpicbg.spim.data.XmlIoSpimData;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import org.scijava.ui.behaviour.ClickBehaviour;
import org.scijava.ui.behaviour.io.InputTriggerConfig;
import org.scijava.ui.behaviour.util.Behaviours;

import java.util.Collection;

/**
 * Source Selector Behaviour Demo
 */

public class BdvSelectorDemo {
	//TODO CHECK WHETHER THE NUMBER OF TIMEPOINTS ARE KEPT
	static public void main(String... args) throws Exception {
		// Creates a demo bdv frame with demo images
		BdvHandle bdvh = initAndShowSources();
		bdvh.getViewerPanel().setNumTimepoints(10);

		// Set up a source selection mode with a trigger input key that toggles it
		// on
		// and off
		SourceSelectorBehaviour ssb = new SourceSelectorBehaviour(bdvh, "E");

		// Adds a listener which displays the events - either GUI or
		// programmatically triggered
		ssb.addSelectedSourcesListener(new SelectedSourcesListener() {

			@Override
			public void selectedSourcesUpdated(
				Collection<SourceAndConverter<?>> selectedSources, String triggerMode)
			{
				bdvh.getViewerPanel().showMessage("Trigger : " + triggerMode);
				bdvh.getViewerPanel().showMessage("Total Selected Sources : " +
					selectedSources.size());
			}

			@Override
			public void lastSelectionEvent(
				Collection<SourceAndConverter<?>> lastSelectedSources, String mode,
				String triggerMode)
			{
				bdvh.getViewerPanel().showMessage(mode + " " + lastSelectedSources
					.size());
			}
		});

		// Example of simple behaviours that can be added on top of the source
		// selector
		// Here it adds an editor behaviour which only action is to remove the
		// selected sources from the window
		// When the delete key is pressed
		addEditorBehaviours(bdvh, ssb);

		// Programmatic API Demo : triggers a list of actions separated in time
		// programmaticAPIDemo(bdvh, ssb);
		// NOTE:
		showOverlay(bdvh, new SourceNameOverlay(bdvh.getViewerPanel()), "Sources Name");
	}

	/**
	 * Helper function that maintains the number of timepoints of a bdv,
	 * see
	 * @param bdvh
	 * @param overlay
	 * @param name
	 * @return
	 * @param <T>
	 */
	public static <T extends BdvOverlay> BdvOverlaySource<T> showOverlay(BdvHandle bdvh, T overlay, String name) {
		// Store
		int nTimepoints = bdvh.getViewerPanel().state().getNumTimepoints();
		int currentTimePoint = bdvh.getViewerPanel().state().getCurrentTimepoint();
		BdvOverlaySource<T> bos = BdvFunctions.showOverlay(overlay, name, BdvOptions.options().addTo(bdvh));
		bdvh.getViewerPanel().state().setNumTimepoints(nTimepoints);
		bdvh.getViewerPanel().state().setCurrentTimepoint(currentTimePoint);
		return bos;
	}

	public static void removeOverlay(BdvOverlaySource<?> overlay) {
		// Store
		BdvHandle bdvh = overlay.getBdvHandle();
		int nTimepoints = bdvh.getViewerPanel().state().getNumTimepoints();
		int currentTimePoint = bdvh.getViewerPanel().state().getCurrentTimepoint();
		overlay.removeFromBdv();
		bdvh.getViewerPanel().state().setNumTimepoints(nTimepoints);
		bdvh.getViewerPanel().state().setCurrentTimepoint(currentTimePoint);
	}

	static BdvHandle initAndShowSources() throws Exception {
		// load and convert the famous blobs image
		ImagePlus imp = IJ.openImage("src/test/resources/blobs.tif");
		RandomAccessibleInterval<UnsignedByteType> blob = ImageJFunctions.wrapReal(
			imp);

		// load 3d mri image spimdataset
		SpimData sd = new XmlIoSpimData().load("src/test/resources/mri-stack.xml");

		// Display mri image
		BdvStackSource<?> bss = BdvFunctions.show(sd).get(0);
		bss.setDisplayRange(0, 255);

		// Gets reference of BigDataViewer
		BdvHandle bdvh = bss.getBdvHandle();

		// Defines location of blobs image
		AffineTransform3D m = new AffineTransform3D();
		m.rotate(0, Math.PI / 4);
		m.translate(256, 0, 0);

		// Display first blobs image - four times to emulate a multichannel image
		BdvFunctions.show(blob, "Blobs Rot X", BdvOptions.options().sourceTransform(
			m).addTo(bdvh));
		BdvFunctions.show(blob, "Blobs Rot X_2", BdvOptions.options().sourceTransform(
				m).addTo(bdvh));
		BdvFunctions.show(blob, "Blobs Rot X_3", BdvOptions.options().sourceTransform(
				m).addTo(bdvh));
		BdvFunctions.show(blob, "Blobs Rot X_4", BdvOptions.options().sourceTransform(
				m).addTo(bdvh));

		// Defines location of blobs image
		m.identity();
		m.rotate(2, Math.PI / 4);
		m.translate(0, 256, 0);

		// Display second blobs image
		BdvFunctions.show(blob, "Blobs Rot Z ", BdvOptions.options()
			.sourceTransform(m).addTo(bdvh));

		// Defines location of blobs image
		m.identity();
		m.rotate(2, Math.PI / 6);
		m.rotate(0, Math.PI / 720);
		m.translate(312, 256, 0);

		// Display third blobs image
		BdvFunctions.show(blob, "Blobs Rot Z Y ", BdvOptions.options()
			.sourceTransform(m).addTo(bdvh));

		// Sets BigDataViewer view
		m.identity();
		m.scale(0.75);
		m.translate(150, 100, 0);

		bdvh.getViewerPanel().state().setViewerTransform(m);
		bdvh.getViewerPanel().requestRepaint();

		return bdvh;
	}

	static void addEditorBehaviours(BdvHandle bdvh, SourceSelectorBehaviour ssb) {
		Behaviours editor = new Behaviours(new InputTriggerConfig());

		ClickBehaviour delete = (x, y) -> bdvh.getViewerPanel().state()
			.removeSources(ssb.getSelectedSources());

		editor.behaviour(delete, "remove-sources-from-bdv", "DELETE");

		// One way to chain the behaviour : install and uninstall on source selector
		// toggling:
		// The delete key will act only when the source selection mode is on
		ssb.addToggleListener(new ToggleListener() {

			@Override
			public void isEnabled() {
				bdvh.getViewerPanel().showMessage("Selection Mode Enable");
				bdvh.getViewerPanel().showMessage(ssb.getSelectedSources().size() +
					" sources selected");
				// Enable the editor behaviours when the selector is enabled
				editor.install(bdvh.getTriggerbindings(), "sources-editor");
			}

			@Override
			public void isDisabled() {
				bdvh.getViewerPanel().showMessage("Selection Mode Disable");
				// Disable the editor behaviours the selector is disabled
				bdvh.getTriggerbindings().removeInputTriggerMap("sources-editor");
				bdvh.getTriggerbindings().removeBehaviourMap("sources-editor");
			}
		});
	}

	static void programmaticAPIDemo(BdvHandle bdvh, SourceSelectorBehaviour ssb)
		throws Exception
	{
		// Enable source selection mode
		ssb.enable();

		// Select a source
		ssb.selectedSourceAdd(bdvh.getViewerPanel().state().getSources().get(0));

		Thread.sleep(1000);

		// and another one
		ssb.selectedSourceAdd(bdvh.getViewerPanel().state().getSources().get(1));

		Thread.sleep(1000);

		// and another one
		ssb.selectedSourceAdd(bdvh.getViewerPanel().state().getSources().get(2));

		Thread.sleep(1000);

		// and another one and remove one
		ssb.selectedSourceAdd(bdvh.getViewerPanel().state().getSources().get(3));
		ssb.selectedSourceRemove(bdvh.getViewerPanel().state().getSources().get(0));

		Thread.sleep(1000);
		// clears all selection
		ssb.selectedSourcesClear();

		Thread.sleep(1000);
		// select all
		ssb.selectedSourceAdd(bdvh.getViewerPanel().state().getSources());
	}
}
