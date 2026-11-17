package com.sxdpz23.navisarthi

import com.sxdpz23.navisarthi.dash.map.wallpaperCrop
import org.junit.Assert.*
import org.junit.Test

class WallpaperCropTest {
    @Test fun portraitAndLandscapeStayInsideImageAtEverySliderExtreme() {
        for ((w,h) in listOf(2048 to 600, 600 to 2048, 1 to 1)) {
            for (zoom in listOf(1f, 2f, 4f)) for (x in listOf(0f,.5f,1f)) for (y in listOf(0f,.5f,1f)) {
                val crop = wallpaperCrop(w,h,zoom,x,y)
                assertTrue(crop.side > 0)
                assertTrue(crop.x >= 0 && crop.y >= 0)
                assertTrue(crop.x + crop.side <= w && crop.y + crop.side <= h)
            }
        }
    }
    @Test fun initialCropIsCentredAndZoomRetainsSelectedEdge() {
        val centre = wallpaperCrop(1200,800,1f,.5f,.5f)
        assertEquals(200,centre.x); assertEquals(0,centre.y); assertEquals(800,centre.side)
        val edge = wallpaperCrop(1200,800,2f,1f,1f)
        assertEquals(800,edge.x); assertEquals(400,edge.y); assertEquals(400,edge.side)
    }
    @Test fun sliderValuesAreClamped() {
        assertEquals(wallpaperCrop(1200,800,4f,0f,1f), wallpaperCrop(1200,800,10f,-1f,2f))
    }
}
