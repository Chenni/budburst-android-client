package edu.ucla.cens.budburst;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Iterator;

import models.ObservationRow;
import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.Message;
import android.util.Log;
import edu.ucla.cens.budburst.data.Row;
import edu.ucla.cens.budburst.data.SyncableDatabase;
import edu.ucla.cens.budburst.helper.Download;
import edu.ucla.cens.budburst.helper.Downloadable;
import edu.ucla.cens.budburst.helper.netUtils;

public class SyncDatabases extends Activity implements Downloadable {
	private static final String TAG = "Budburst";

	private static final int DOWNLOADED_SITES = 0;
	private static final int DOWNLOADED_OBSERVATIONS = 1;
	private static final int DOWNLOADED_PLANTS = 2;
	private static final int DOWNLOADED_OBSERVATION_IMAGE = 3;

	private BudburstDatabaseManager dbManager;
	private DownloadManager downloadManager;

	private ArrayList<Download> downloads = new ArrayList<Download>();

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);

		dbManager = Budburst.getDatabaseManager();
		downloadManager = Budburst.getDownloadManager();

		String url = ((SyncableDatabase) dbManager.getDatabase("site")).getURL() + PreferencesManager.currentGETAuthParams(this);
		Download d = new Download(url);
		downloads.add(d);
		downloadManager.download(this, DOWNLOADED_SITES, d);

		url = ((SyncableDatabase) dbManager.getDatabase("observation")).getURL() + PreferencesManager.currentGETAuthParams(this);
		d = new Download(url);
		downloads.add(d);
		downloadManager.download(this, DOWNLOADED_OBSERVATIONS, d);

	}

	public Object consumeInputStream(Message msg) {
		Log.d(TAG, "consumeInputStream");
		switch (msg.what) {
		case DOWNLOADED_SITES:
		case DOWNLOADED_OBSERVATIONS:
		case DOWNLOADED_PLANTS:
			return netUtils.generateString((InputStream) msg.obj);
		case DOWNLOADED_OBSERVATION_IMAGE:
			return BitmapFactory.decodeStream((InputStream) msg.obj);
		}
		return null;
	}

	public void onDownloaded(Message msg, Download d) {
		String db = "";
		switch (msg.what) {
		case DOWNLOADED_SITES:
			((SyncableDatabase) dbManager.getDatabase("site")).sync((String) msg.obj);

			// we can start downloading plants now that we know what the sites are
			ArrayList<Row> sites = dbManager.getDatabase("site").all();
			for (Iterator<Row> i = sites.iterator(); i.hasNext();) {
				String url = ((SyncableDatabase) dbManager.getDatabase("plant")).getURL() + PreferencesManager.currentGETAuthParams(this);
				url += "&site_id=" + i.next()._id;
				Download plantd = new Download(url);
				downloads.add(plantd);
				downloadManager.download(this, DOWNLOADED_PLANTS, plantd);
			}
			break;
		case DOWNLOADED_OBSERVATIONS:
			((SyncableDatabase) dbManager.getDatabase("observation")).sync((String) msg.obj);
			// we can start downloading observation images now that we know what observations we have
			ArrayList<Row> observations = dbManager.getDatabase("observation").all();
			for (Iterator<Row> i = observations.iterator(); i.hasNext();) {
				ObservationRow current = (ObservationRow) i.next();
				if (!new File(current.getImagePath()).exists()) {
					String url = getString(R.string.observationImageURL) + "?image_id=" + current.image_id;
					Download plantd = new Download(url);
					downloads.add(plantd);
					downloadManager.download(this, DOWNLOADED_OBSERVATION_IMAGE, plantd);
				}
			}

			break;
		case DOWNLOADED_PLANTS:
			((SyncableDatabase) dbManager.getDatabase("plant")).sync((String) msg.obj);
			break;
		case DOWNLOADED_OBSERVATION_IMAGE:
			ObservationRow o = (ObservationRow) dbManager.getDatabase("observation").find("image_id=" + d.url.split("=")[1]).get(0);
			try {
				FileOutputStream out = new FileOutputStream(o.getImagePath());
				((Bitmap) msg.obj).compress(Bitmap.CompressFormat.PNG, 90, out);
			} catch (FileNotFoundException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			break;
		}

		downloads.remove(d);
		if (downloads.isEmpty())
			finish();

	}
}