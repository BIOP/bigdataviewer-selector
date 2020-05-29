package ch.epfl.biop.bdv.select;

import bdv.tools.boundingbox.TransformedBox;
import bdv.util.BdvOverlay;
import bdv.viewer.SourceAndConverter;
import bdv.viewer.ViewerPanel;
import bdv.tools.boundingbox.RenderBoxHelper;
import net.imglib2.FinalRealInterval;
import net.imglib2.RealInterval;
import net.imglib2.realtransform.AffineTransform3D;
import org.scijava.ui.behaviour.DragBehaviour;
import org.scijava.ui.behaviour.util.Behaviours;

import java.awt.*;
import java.awt.geom.GeneralPath;
import java.util.*;
import java.util.List;

/**
 * Works with {@link SourceSelectorBehaviour}
 *
 * Displays box overlays on top of visible sources of all visible {@link SourceAndConverter} of a {@link ViewerPanel}
 *
 * The coloring differs depending on the state (selected or not selected)
 *
 * An inner interface {@link OverlayStyle} is used to define how the overlay box are colored.
 * The coloring can be modified with the {@link SourceSelectorOverlay#getStyles()} function
 * and by modify using the values contained in "DEFAULT" and "SELECTED"
 *
 * GUI functioning:
 *
 * The user can draw a rectangle and all sources which interesects this rectangle AT THE CURRENT PLANE SLICING of Bdv
 * will be involved in the next selection change event.
 * Either the user was holding no extra key:
 * - the involved sources will define the new selection set
 * The user was holding CTRL:
 * - the involved sources will be removed from the current selection set
 * The user was holding SHIFT:
 * - the involved sources are added to the current selection set
 * Note : changing the key pressing DURING the rectangle drawing will not be taken into account,
 * contrary to an expected standard behaviour TODO : can this be improved ?
 *
 * Note : The user can perform a single click as well with the modifier keys, no need to drag
 * this is because a single click also triggers a {@link DragBehaviour}
 *
 * Note : the overlay can be very slow to draw - because it's java graphics 2D... It's
 * especially visible is the zoom is very big... Clipping is badly done TODO ?
 *
 * @author Nicolas Chiaruttini, BIOP, EPFL, 2020
 *
 */

public class SourceSelectorOverlay extends BdvOverlay {

    final ViewerPanel viewer;

    boolean isCurrentlySelecting = false;

    int xCurrentSelectStart, yCurrentSelectStart, xCurrentSelectEnd, yCurrentSelectEnd;

    private int canvasWidth;

    private int canvasHeight;

    private List<SourceBoxOverlay> sourcesBoxOverlay = new ArrayList<>();

    Map<String, OverlayStyle> styles = new HashMap<>();

    final SourceSelectorBehaviour ssb;

    public SourceSelectorOverlay(ViewerPanel viewer, SourceSelectorBehaviour ssb) {
        this.ssb = ssb;
        this.viewer = viewer;
        updateBoxes();
        styles.put("DEFAULT", new DefaultOverlayStyle());
        styles.put("SELECTED", new SelectedOverlayStyle());
    }

    protected void addSelectionBehaviours(Behaviours behaviours) {
        behaviours.behaviour( new RectangleSelectSourcesBehaviour( SourceSelectorBehaviour.SET ), "select-set-sources", new String[] { "button1" });
        behaviours.behaviour( new RectangleSelectSourcesBehaviour( SourceSelectorBehaviour.ADD ), "select-add-sources", new String[] { "shift button1" });
        behaviours.behaviour( new RectangleSelectSourcesBehaviour( SourceSelectorBehaviour.REMOVE ), "select-remove-sources", new String[] { "ctrl button1" });
    }

    public Map<String, OverlayStyle> getStyles() {
        return styles;
    }

    synchronized void startCurrentSelection(int x, int y) {
        xCurrentSelectStart = x;
        yCurrentSelectStart = y;
    }

    synchronized void updateCurrentSelection(int xCurrent, int yCurrent) {
        xCurrentSelectEnd = xCurrent;
        yCurrentSelectEnd = yCurrent;
        isCurrentlySelecting = true;
    }

    synchronized void endCurrentSelection(int x, int y, String mode) {
        xCurrentSelectEnd = x;
        yCurrentSelectEnd = y;
        isCurrentlySelecting = false;
        // Selection is done : but we need to access the trigger keys to understand what's happening
        Set<SourceAndConverter<?>> currentSelection = this.getLastSelectedSources();

        ssb.processSelectionModificationEvent(getLastSelectedSources(), mode, "SelectorOverlay");

    }

    Rectangle getCurrentSelectionRectangle() {
        int x0, y0, w, h;
        if (xCurrentSelectStart>xCurrentSelectEnd) {
            x0 = xCurrentSelectEnd;
            w = xCurrentSelectStart-xCurrentSelectEnd;
        } else {
            x0 = xCurrentSelectStart;
            w = xCurrentSelectEnd-xCurrentSelectStart;
        }
        if (yCurrentSelectStart>yCurrentSelectEnd) {
            y0 = yCurrentSelectEnd;
            h = yCurrentSelectStart-yCurrentSelectEnd;
        } else {
            y0 = yCurrentSelectStart;
            h = yCurrentSelectEnd-yCurrentSelectStart;
        }
        // Hack : allows selection on double or single click
        if (w==0) w = 1;
        if (h==0) h = 1;
        return new Rectangle(x0, y0, w, h);
    }

    Set<SourceAndConverter<?>> getLastSelectedSources() {
        Set<SourceAndConverter<?>> lastSelected = new HashSet<>();

        final RenderBoxHelper rbh = new RenderBoxHelper();

        // We need to find whether a rectangle in real space intersects a box in 3d -> Makes use of the work previously done in RenderBoxHelper
        for (SourceBoxOverlay sbo : sourcesBoxOverlay) {
          //
            AffineTransform3D viewerTransform = new AffineTransform3D();
            viewer.state().getViewerTransform(viewerTransform);
            AffineTransform3D transform = new AffineTransform3D();
            synchronized ( viewerTransform )
            {
                sbo.getTransform( transform );
                transform.preConcatenate( viewerTransform );
            }
            final double ox = canvasWidth / 2;
            final double oy = canvasHeight / 2;

            rbh.setOrigin( ox, oy );
            rbh.setScale( 1 );

            final GeneralPath front = new GeneralPath();
            final GeneralPath back = new GeneralPath();
            final GeneralPath intersection = new GeneralPath();

            rbh.renderBox( sbo.getInterval(), transform, front, back, intersection );

            Rectangle r = getCurrentSelectionRectangle();

            if (intersection.intersects(r)||intersection.contains(r)) {
                lastSelected.add(sbo.sac);
            }
        }
        return lastSelected;
    }

    @Override
    public synchronized void draw(Graphics2D g) {
        for (SourceBoxOverlay source : sourcesBoxOverlay) {
            source.drawBoxOverlay(g);
        }

        if (isCurrentlySelecting) {
            g.setStroke( styles.get("SELECTED").getNormalStroke() );
            g.setPaint( styles.get("SELECTED").getBackColor() );
            g.draw(getCurrentSelectionRectangle());
        }

    }

    @Override
    public void setCanvasSize( final int width, final int height ) {
        this.canvasWidth = width;
        this.canvasHeight = height;
    }

    public void updateBoxes() {
        sourcesBoxOverlay.clear();
        for (SourceAndConverter sac : viewer.state().getVisibleSources()) {
            if (sac.getSpimSource().getSource(0,0)!=null) { // TODO : fix hack to avoid dirty overlay filter
                sourcesBoxOverlay.add(new SourceSelectorOverlay.SourceBoxOverlay(sac));
            }
        }
    }

    class SourceBoxOverlay implements TransformedBox {

        final SourceAndConverter<?> sac;

        final RenderBoxHelper rbh;

        public SourceBoxOverlay(SourceAndConverter sac) {
            this.sac = sac;
            rbh = new RenderBoxHelper();
        }

        public void drawBoxOverlay(Graphics2D graphics) {
            OverlayStyle os;

            if (ssb.selectedSources.contains(sac)) {
                os = styles.get("SELECTED");
            } else {
                os = styles.get("DEFAULT");
            }
            final GeneralPath front = new GeneralPath();
            final GeneralPath back = new GeneralPath();
            final GeneralPath intersection = new GeneralPath();

            final RealInterval interval = getInterval();
            final double ox = canvasWidth / 2;
            final double oy = canvasHeight / 2;
            AffineTransform3D viewerTransform = new AffineTransform3D();
            viewer.state().getViewerTransform(viewerTransform);
            AffineTransform3D transform = new AffineTransform3D();
            synchronized ( viewerTransform )
            {
                getTransform( transform );
                transform.preConcatenate( viewerTransform );
            }
            rbh.setOrigin( ox, oy );
            rbh.setScale( 1 );
            rbh.renderBox( interval, transform, front, back, intersection );

            graphics.setRenderingHint( RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON );

            graphics.setStroke( os.getNormalStroke() );
            graphics.setPaint( os.getBackColor() );
            graphics.draw( back );

            graphics.setPaint( os.getFrontColor() );
            graphics.draw( front );

            graphics.setPaint( os.getIntersectionFillColor() );
            graphics.fill( intersection );

            graphics.setPaint( os.getIntersectionColor() );
            graphics.setStroke( os.getIntersectionStroke() );
            graphics.draw( intersection );
        }

        @Override
        public RealInterval getInterval() {
            long[] dims = new long[3];
            sac.getSpimSource().getSource(viewer.state().getCurrentTimepoint(),0).dimensions(dims);
            return new FinalRealInterval(new double[]{-0.5,-0.5,-0.5}, new double[]{dims[0]-0.5, dims[1]-0.5, dims[2]-0.5});
        }

        @Override
        public void getTransform(AffineTransform3D transform) {
            sac.getSpimSource().getSourceTransform(viewer.state().getCurrentTimepoint(),0,transform);
        }
    }

    /**
     * STYLES
     */

    public interface OverlayStyle {

        Color getBackColor();

        Color getFrontColor();

        Color getIntersectionColor();

        Color getIntersectionFillColor();

        Stroke getNormalStroke();

        Stroke getIntersectionStroke();

    }

    public class DefaultOverlayStyle implements SourceSelectorOverlay.OverlayStyle {
        Color backColor = new Color( 0x00994499 );

        Color frontColor = new Color(0x5D46B6);

        Color intersectionFillColor = new Color(0x32994499, true );

        Stroke normalStroke = new BasicStroke();

        Stroke intersectionStroke = new BasicStroke( 1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 1f, new float[] { 10f, 10f }, 0f );

        Color intersectionColor = Color.WHITE.darker();

        public Color getBackColor() {
            return backColor;
        }

        public Color getFrontColor() {
            return frontColor;
        }

        @Override
        public Color getIntersectionColor() {
            return intersectionColor;
        }

        public Color getIntersectionFillColor() {
            return intersectionFillColor;
        }

        public Stroke getNormalStroke() {
            return normalStroke;
        }

        @Override
        public Stroke getIntersectionStroke() {
            return intersectionStroke;
        }
    }

    public class SelectedOverlayStyle implements SourceSelectorOverlay.OverlayStyle {
        Color backColor = new Color(0xF7BF18);

        Color frontColor = new Color(0xC7F718);

        Color intersectionFillColor = new Color(0x30B66A00, true );

        Stroke normalStroke = new BasicStroke();

        Stroke intersectionStroke = new BasicStroke( 1f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 1f, new float[] { 10f, 10f }, 0f );

        Color intersectionColor = Color.WHITE.darker();

        public Color getBackColor() {
            return backColor;
        }

        public Color getFrontColor() {
            return frontColor;
        }

        @Override
        public Color getIntersectionColor() {
            return intersectionColor;
        }

        public Color getIntersectionFillColor() {
            return intersectionFillColor;
        }

        public Stroke getNormalStroke() {
            return normalStroke;
        }

        @Override
        public Stroke getIntersectionStroke() {
            return intersectionStroke;
        }

    }

    /**
     * Drag Selection Behaviour
     */
    class RectangleSelectSourcesBehaviour implements DragBehaviour {

        final String mode;

        public RectangleSelectSourcesBehaviour(String mode) {
            this.mode = mode;
        }

        @Override
        public void init(int x, int y) {
            startCurrentSelection(x,y);
            viewer.setCursor(new Cursor(Cursor.CROSSHAIR_CURSOR));
            switch(mode) {
                case SourceSelectorBehaviour.SET :
                    viewer.showMessage("Set Selection" );
                    break;
                case SourceSelectorBehaviour.ADD :
                    viewer.showMessage("Add Selection" );
                    break;
                case SourceSelectorBehaviour.REMOVE :
                    viewer.showMessage("Remove Selection" );
                    break;
            }
        }

        @Override
        public void drag(int x, int y) {
            updateCurrentSelection(x,y);
            viewer.paint(); // TODO : understand how to remove it from here ?
        }

        @Override
        public void end(int x, int y) {
            endCurrentSelection(x,y,mode);
            viewer.setCursor(new Cursor(Cursor.DEFAULT_CURSOR));
        }
    }

}