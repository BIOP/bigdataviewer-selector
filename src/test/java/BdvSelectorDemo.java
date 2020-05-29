import bdv.util.BdvFunctions;
import bdv.util.BdvHandle;
import bdv.util.BdvOptions;
import bdv.util.BdvStackSource;
import bdv.viewer.SourceAndConverter;
import ch.epfl.biop.bdv.edit.SelectedSourcesListener;
import ch.epfl.biop.bdv.edit.SourceSelectorBehaviour;
import ch.epfl.biop.bdv.edit.ToggleListener;
import ij.IJ;
import ij.ImagePlus;
import mpicbg.spim.data.SpimData;
import mpicbg.spim.data.XmlIoSpimData;
import mpicbg.spim.data.generic.XmlIoAbstractSpimData;
import net.imagej.ImageJ;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.view.Views;
import org.scijava.ui.behaviour.ClickBehaviour;
import org.scijava.ui.behaviour.io.InputTriggerConfig;
import org.scijava.ui.behaviour.util.Behaviours;

import java.util.Collection;

/**
 * Goal : mimick Blender v>2.8
 * Blender
 * Select Left
 *
 * CardPanel have icons mimicking blender
 */

public class BdvSelectorDemo {

    static public void main(String... args) throws Exception {

        // load and convert an image
        ImagePlus imp = IJ.openImage("src/test/resources/blobs.tif");
        RandomAccessibleInterval blob = ImageJFunctions.wrapReal(imp);

        SpimData sd = new XmlIoSpimData().load("src/test/resources/mri-stack.xml");

        AffineTransform3D m = new AffineTransform3D();
        m.rotate(0,Math.PI/4);
        m.translate(256, 0,0);

        BdvStackSource bss = BdvFunctions.show(sd).get(0);
        bss.setDisplayRange(0,255);

        BdvHandle bdvh = bss.getBdvHandle();

        BdvFunctions.show(blob, "Blobs Rot X", BdvOptions.options().sourceTransform(m).addTo(bdvh));

        m.identity();

        m.rotate(2,Math.PI/4);

        m.translate(0,256,0);

        BdvFunctions.show(blob, "Blobs Rot Z ", BdvOptions.options().sourceTransform(m).addTo(bdvh));

        m.identity();

        m.rotate(2,Math.PI/6);

        m.rotate(0,Math.PI/720);

        m.translate(312,256,0);

        BdvFunctions.show(blob, "Blobs Rot Z Y ", BdvOptions.options().sourceTransform(m).addTo(bdvh));

        m.identity();
        m.scale(0.75);
        m.translate(150,100,0);

        bdvh.getViewerPanel().setCurrentViewerTransform(m);
        bdvh.getViewerPanel().requestRepaint();

        SourceSelectorBehaviour ssb = new SourceSelectorBehaviour(bdvh, "E");

        Behaviours editor = new Behaviours(new InputTriggerConfig());

        ClickBehaviour delete = (x,y) -> bdvh.getViewerPanel().state().removeSources(ssb.getSourceSelectorOverlay().getSelectedSources());

        editor.behaviour(delete, "remove-sources-from-bdv", new String[]{"DELETE"});

        ssb.addToggleListener(new ToggleListener() {
            @Override
            public void enable() {
                editor.install(bdvh.getTriggerbindings(), "sources-editor");
                bdvh.getViewerPanel().showMessage("Selection Mode");
                bdvh.getViewerPanel().showMessage(ssb.getSelectedSources().size()+" sources selected");
            }

            @Override
            public void disable() {
                bdvh.getTriggerbindings().removeInputTriggerMap("sources-editor");
                bdvh.getTriggerbindings().removeBehaviourMap("sources-editor");
                bdvh.getViewerPanel().showMessage("Navigation Mode");
            }
        });

        ssb.addSelectedSourcesListener(new SelectedSourcesListener() {

            @Override
            public void selectedSourcesUpdated(Collection<SourceAndConverter<?>> selectedSources) {
                bdvh.getViewerPanel().showMessage("Total Selected Sources : "+selectedSources.size());
            }

            @Override
            public void lastSelectionEvent(Collection<SourceAndConverter<?>> lastSelectedSources, String mode) {
                bdvh.getViewerPanel().showMessage(mode + " " + lastSelectedSources.size());
            }
        });

        ssb.enable();
    }
}
