package com.Gallery.X

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.Gallery.X.media.UniversalImageLoader

class GalleryXApplication : Application(), ImageLoaderFactory {
    override fun newImageLoader(): ImageLoader {
        return UniversalImageLoader.get(this)
    }
}
