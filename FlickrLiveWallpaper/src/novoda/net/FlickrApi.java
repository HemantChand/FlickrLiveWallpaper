
package novoda.net;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.DecimalFormat;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.Random;

import novoda.wallpaper.flickr.models.Photo;
import novoda.wallpaper.flickr.models.PhotoSearch;

import org.apache.http.HttpResponse;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.location.Location;
import android.util.Log;
import android.util.Pair;

public class FlickrApi {

    public Pair<Bitmap, String> retrievePhoto(boolean fRAMED, Location location, String placeName) throws NoFlickrImagesFoundException{
        // List<Photo> list = getPhotosFromExactLocation(location);
        List<Photo> photos = photosFromApproxLocation(placeName, location);
        
        if(photos.isEmpty()){
            throw new NoFlickrImagesFoundException();
        }
        
        try {
            chosenPhoto = choosePhoto(photos);
        } catch (NoFlickrImagesFoundException e) {
            Log.e(TAG, "No Photos were in the collection!");
            throw new NoFlickrImagesFoundException();
        }

        Bitmap cachedBitmap = null;
        if (chosenPhoto != null) {

            URL url = null;
            try {
                if (fRAMED) {
                    url = new URL(chosenPhoto.getUrl());
                } else {
                    Log.i(TAG, "Image is not framed so it will be a large download");
                    url = new URL(chosenPhoto.getUrl(Photo.ORIGINAL_IMG_URL));
                }
                Log.d(TAG, "Requesting static image from Flickr=[" + url + "]");

            } catch (MalformedURLException error) {
                error.printStackTrace();
            }

            cachedBitmap = retrieveBitmap(url);
        }

        return new Pair<Bitmap, String>(cachedBitmap, chosenPhoto.getFullFlickrUrl());
    }

    public Bitmap retrieveBitmap(URL photoUrl) {
        Bitmap bitmap = null;

        int retries = 0;
        do {
            if (retries > 0) {
                Log.e(TAG, "Couldn't retrieve Photo retrying: " + retries);
            }
            try {
                HttpResponse response = webSrvMgr.getHTTPResponse(photoUrl);
                InputStream input = response.getEntity().getContent();
                bitmap = BitmapFactory.decodeStream(input);
            }catch (IllegalArgumentException e) {
                Log.e(TAG, "Could not retireve bitmap wth null URL", e);
            }catch (IOException e) {
                Log.e(TAG, "Could not retrieve bitmap from resulting httpResponse", e);
            }
            retries++;
        } while (bitmap == null && retries < 3);

        return bitmap;
    }

    /*
     * Establish current place name via the GeoName API Query Use place name to
     * establish if photos are available as a tag on Flickr Requery if photos
     * can be divided into pages (to help randomness of results)
     */
    private List<Photo> photosFromApproxLocation(String placeNameTag, Location location) {

        // Add random to ensure varying results
        photoSearch.with("accuracy", "11");
        photoSearch.with("tags", placeNameTag);
        photoSearch.with("sort", "interestingness-desc");
        photoSearch.with("media", "photos");
        photoSearch.with("extras", "url_s,url_m,original_format,path_alias,url_sq,url_t");
        photoSearch.with("per_page", "50");

        List<Photo> list = photoSearch.fetchStructuredDataList();

        if (list.size() > 1) {
            int square = (int)Math.sqrt(list.size());
            photoSearch.with("per_page", "" + square);
            photoSearch.with("page", "" + randomWheel.nextInt(square - 1));
            list = photoSearch.fetchStructuredDataList();
        }

        return list;
    }

    /*
     * Chosen an image from within a list of suitable photo specs.
     */
    private Photo choosePhoto(List<Photo> photos) throws NoFlickrImagesFoundException{
        Log.v(TAG, "Choosing a photo from amoungst those with URLs");
        for (int i = 0; i < photos.size(); i++) {
            if (photos.get(i).origResImg_url == null || photos.get(i).medResImg_url == null
                    || photos.get(i).smallResImg_url == null) {
                photos.remove(i);
            }
        }
        if (!photos.isEmpty()) {
            if(photos.size()>1){
                chosenPhoto = photos.get(randomWheel.nextInt(photos.size() - 1));
                return chosenPhoto;
            }else{
                Log.w(TAG, "There was only one photo found");
                return photos.get(0);
            }
        }
        
        throw new NoFlickrImagesFoundException();
    }

    /*
     * Return Flickr photos based on the exact user's location
     */
    @SuppressWarnings("unused")
    private List<Photo> getPhotosFromExactLocation(Location location) {
        Log.d(TAG, "Requesting photo details based on exact location");
        final Flickr<Photo> photoSearch = new PhotoSearch();

        double latitude = location.getLatitude();
        double longitude = location.getLongitude();

        // Random no. between 0.1 > 0.0099
        double d = randomWheel.nextDouble();
        d = Double.valueOf(df.format((d * (0.1 - 0.0099))));

        Log.i(TAG, "Original Longitude=[" + longitude + "] latitude=[" + latitude + "]");
        Log.i(TAG, "Ammended Longitude=[" + (df.format(longitude + d)) + "] latitude=["
                + (df.format(latitude + d)) + "]");
        // Add random to ensure varying results

        photoSearch.with("lat", "" + df.format(latitude + d));
        photoSearch.with("lon", "" + df.format(longitude + d));
        photoSearch.with("accuracy", "1");
        photoSearch.with("tags", getPeriodOfDay(new GregorianCalendar().get(Calendar.HOUR_OF_DAY)));
        photoSearch.with("sort", "interestingness-desc");
        photoSearch.with("media", "photos");
        photoSearch.with("extras", "url_m");
        photoSearch.with("per_page", "1");
        // f.with("page", ""+ randomWheel.nextInt(5));
        List<Photo> list = photoSearch.fetchStructuredDataList();

        if (list.size() < 1) {
            photoSearch.remove("accuracy", "16");
            photoSearch.with("accuracy", "11");
        }

        for (int i = 0; i < list.size(); i++) {
            Log.i(TAG, "Photo in list= " + list.get(i).toString());
        }

        return list;
    }

    /**
     * Returns a human readable tag which will be used with the search query.
     * 
     * @param 24h clock format
     * @return Period of Day
     */
    private String getPeriodOfDay(int time) {
        if ((time > 22 && time <= 24) || (time >= 0 && time <= 5)) {
            return "night";
        }
        if (time > 5 && time <= 7) {
            return "dawn";
        }
        if (time > 7 && time <= 11) {
            return "morning";
        }
        if (time > 11 && time <= 15) {
            return "noon";
        }
        if (time > 15 && time <= 19) {
            return "afternoon";
        }
        if (time > 19 && time <= 22) {
            return "evening";
        }
        // should not be here but just in case as it s getting late
        return "city";
    }

    private static final String TAG = FlickrApi.class.getSimpleName();

    private final WebServiceMgr webSrvMgr = new WebServiceMgr();

    private static final Random randomWheel = new Random();

    private static final DecimalFormat df = new DecimalFormat("#.######");

    private static final PhotoSearch photoSearch = new PhotoSearch();

    private Photo chosenPhoto;

}
