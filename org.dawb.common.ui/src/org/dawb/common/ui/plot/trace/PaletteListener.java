package org.dawb.common.ui.plot.trace;

import java.util.EventListener;

/**
 
 
    Histogramming Explanation
   ---------------------------
   Image intensity distribution:

                ++----------------------**---------------
                +                      *+ *              
                ++                    *    *             
                |                     *    *             
                ++                    *     *            
                *                    *       *            
                +*                   *       *            
                |*                  *        *            
                +*                  *        *           
                |                  *          *         
                ++                 *          *          
                |                  *           *        
                ++                 *           *        
                |                 *            *        
                ++                *            *       
                                 *              *      
        Min Cut           Min    *              *      Max                     Max cut
 Red <- |   (min colour)  |    (color range, palette)  |      (max color)      | -> Blue
                                *                 *  
                |              *        +         *  
----------------++------------**---------+----------**----+---------------**+---------------++

 * @author fcp94556
 *
 */
public interface PaletteListener extends EventListener {

	/**
	 * Called when palette data changed
	 * @param evt
	 */
	public void paletteChanged(PaletteEvent evt);
	
	/**
	 * Called when min changed.
	 * @param evt
	 */
	public void minChanged(PaletteEvent evt);
	
	/**
	 * Called when max changed.
	 * @param evt
	 */
	public void maxChanged(PaletteEvent evt);
	
	/**
	 * Called when max cut changed.
	 * @param evt
	 */
	public void maxCutChanged(PaletteEvent evt);
	
	
	/**
	 * Called when max cut changed.
	 * @param evt
	 */
	public void minCutChanged(PaletteEvent evt);
	
	/**
	 * Called when max cut changed.
	 * @param evt
	 */
	public void nanBoundsChanged(PaletteEvent evt);

	public class Stub implements PaletteListener {

		@Override
		public void paletteChanged(PaletteEvent evt) {
			// TODO Auto-generated method stub
			
		}

		@Override
		public void minChanged(PaletteEvent evt) {
			// TODO Auto-generated method stub
			
		}

		@Override
		public void maxChanged(PaletteEvent evt) {
			// TODO Auto-generated method stub
			
		}

		@Override
		public void maxCutChanged(PaletteEvent evt) {
			// TODO Auto-generated method stub
			
		}

		@Override
		public void minCutChanged(PaletteEvent evt) {
			// TODO Auto-generated method stub
			
		}

		@Override
		public void nanBoundsChanged(PaletteEvent evt) {
			// TODO Auto-generated method stub
			
		}
		
	}

}
