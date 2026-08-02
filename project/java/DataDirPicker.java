/*
Simple DirectMedia Layer
Java source code (C) 2009-2014 Sergii Pylypenko

This software is provided 'as-is', without any express or implied
warranty.  In no event will the authors be held liable for any damages
arising from the use of this software.

Permission is granted to anyone to use this software for any purpose,
including commercial applications, and to alter it and redistribute it
freely, subject to the following restrictions:

1. The origin of this software must not be misrepresented; you must not
   claim that you wrote the original software. If you use this software
   in a product, an acknowledgment in the product documentation would be
   appreciated but is not required. 
2. Altered source versions must be plainly marked as such, and must not be
   misrepresented as being the original software.
3. This notice may not be removed or altered from any source distribution.
*/

package net.sourceforge.clonekeenplus;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.DocumentsContract;
import android.util.Log;
import android.widget.Toast;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Lets the player choose a public (visible without root) game data directory
 * on the first run, or from the settings menu.
 *
 * The chosen directory is stored in SharedPreferences and used as Globals.DataDir
 * before the native code does chdir()/setenv("HOME"), so no C/C++ changes are needed.
 */
class DataDirPicker
{
	public static final int REQ_PICK_DATA_DIR = 43; // Must not clash with SettingsMenuMisc.StorageAccessConfig.REQUEST_STORAGE_ID
	public static final int REQ_ALL_FILES_ACCESS = 44;

	private static final String PREFS_NAME = "openttd_datadir";
	private static final String PREF_USER_DIR = "user_dir";
	private static final String TAG = "SDL";

	// Persisting the user-chosen directory

	static String getUserDir(final Context p)
	{
		try
		{
			SharedPreferences prefs = p.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
			String s = prefs.getString(PREF_USER_DIR, "");
			return s == null ? "" : s;
		}
		catch (Exception e)
		{
			return "";
		}
	}

	static void setUserDir(final Context p, final String path)
	{
		try
		{
			SharedPreferences prefs = p.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
			prefs.edit().putString(PREF_USER_DIR, path).commit();
			Log.i(TAG, "DataDirPicker: saved user data directory: " + path);
		}
		catch (Exception e)
		{
			Log.i(TAG, "DataDirPicker: cannot save preference: " + e);
		}
	}

	static boolean hasUserChoice(final Context p)
	{
		return getUserDir(p).length() > 0;
	}

	// First run guidance

	static boolean needsFirstRunChoice(final Context p)
	{
		return !hasUserChoice(p) && Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED);
	}

	static void startFirstRun(final MainActivity p)
	{
		Log.i(TAG, "DataDirPicker: first run - asking player to pick the game data directory");
		AlertDialog.Builder builder = new AlertDialog.Builder(p);
		builder.setTitle(p.getResources().getString(R.string.data_dir_first_run_title));
		builder.setMessage(p.getResources().getString(R.string.data_dir_first_run_message));
		builder.setPositiveButton(p.getResources().getString(R.string.ok), new DialogInterface.OnClickListener()
		{
			public void onClick(DialogInterface dialog, int item)
			{
				dialog.dismiss();
				proceedFirstRun(p);
			}
		});
		builder.setNegativeButton(p.getResources().getString(R.string.cancel), new DialogInterface.OnClickListener()
		{
			public void onClick(DialogInterface dialog, int item)
			{
				dialog.dismiss();
				p.finishDataDirFirstRun(false);
			}
		});
		builder.setOnCancelListener(new DialogInterface.OnCancelListener()
		{
			public void onCancel(DialogInterface dialog)
			{
				p.finishDataDirFirstRun(false);
			}
		});
		AlertDialog alert = builder.create();
		alert.setOwnerActivity(p);
		alert.show();
	}

	private static void proceedFirstRun(final MainActivity p)
	{
		if( Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager() )
		{
			requestAllFilesAccess(p);
		}
		else
		{
			launchPicker(p);
		}
	}

	static void requestAllFilesAccess(final MainActivity p)
	{
		try
		{
			Intent intent = new Intent(android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
					Uri.parse("package:" + p.getPackageName()));
			p.startActivityForResult(intent, REQ_ALL_FILES_ACCESS);
		}
		catch (Exception e)
		{
			try
			{
				Intent intent = new Intent(android.provider.Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
				p.startActivityForResult(intent, REQ_ALL_FILES_ACCESS);
			}
			catch (Exception e2)
			{
				Log.i(TAG, "DataDirPicker: cannot open all files access settings: " + e2);
				p.finishDataDirFirstRun(false);
			}
		}
	}

	// Called when the player returns from the "All files access" settings page.
	// The permission check is delayed and retried because the permission state may
	// not be refreshed yet right after returning, and the focus-change driven
	// onResume() must not trigger the check before the player finished granting.
	static void onAllFilesAccessResult(final MainActivity p)
	{
		checkAllFilesAccessDelayed(p, 0);
	}

	private static void checkAllFilesAccessDelayed(final MainActivity p, final int attempt)
	{
		Handler handler = new Handler(Looper.getMainLooper());
		handler.postDelayed(new Runnable()
		{
			public void run()
			{
				if( hasAllFilesAccess() )
				{
					launchPicker(p);
				}
				else if( attempt < 2 )
				{
					checkAllFilesAccessDelayed(p, attempt + 1);
				}
				else
				{
					Log.i(TAG, "DataDirPicker: all files access not granted, using default data directory");
					Toast.makeText(p, p.getResources().getString(R.string.data_dir_failed), Toast.LENGTH_LONG).show();
					p.finishDataDirFirstRun(false);
				}
			}
		}, 200);
	}

	static boolean hasAllFilesAccess()
	{
		return Build.VERSION.SDK_INT < Build.VERSION_CODES.R || Environment.isExternalStorageManager();
	}

	static void launchPicker(final MainActivity p)
	{
		try
		{
			Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
			intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
					| Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
			p.startActivityForResult(intent, REQ_PICK_DATA_DIR);
		}
		catch (Exception e)
		{
			Log.i(TAG, "DataDirPicker: cannot launch directory picker: " + e);
			p.finishDataDirFirstRun(false);
		}
	}

	// Handling the picker result

	static void onPicked(final MainActivity p, final int resultCode, final Intent data, final boolean fromMenu)
	{
		String newDir = null;
		if( resultCode == Activity.RESULT_OK && data != null && data.getData() != null )
		{
			Uri treeUri = data.getData();
			try
			{
				p.getContentResolver().takePersistableUriPermission(treeUri,
						Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
			}
			catch (Exception e) {}
			newDir = resolveTreePath(treeUri);
		}

		if( newDir != null )
		{
			File dir = new File(newDir);
			if( dir.isDirectory() || dir.mkdirs() )
			{
				if( migrateData(p, newDir) )
				{
					setUserDir(p, newDir);
					Toast.makeText(p, p.getResources().getString(R.string.data_dir_changed) + "\n" + newDir, Toast.LENGTH_LONG).show();
					if( fromMenu )
					{
						restartApp(p);
					}
					else
					{
						p.finishDataDirFirstRun(true);
					}
					return;
				}
			}
		}

		Log.i(TAG, "DataDirPicker: cannot use the selected directory, falling back to default");
		Toast.makeText(p, p.getResources().getString(R.string.data_dir_failed), Toast.LENGTH_LONG).show();
		if( fromMenu )
		{
			SettingsMenu.goBack(p);
		}
		else
		{
			p.finishDataDirFirstRun(false);
		}
	}

	static void restartApp(final MainActivity p)
	{
		try
		{
			Intent intent = new Intent(p, RestartMainActivity.class);
			p.startActivity(intent);
			System.exit(0);
		}
		catch (Exception e)
		{
			Log.i(TAG, "DataDirPicker: restart failed: " + e);
		}
	}

	// SAF tree URI -> real path

	static String resolveTreePath(final Uri treeUri)
	{
		try
		{
			String docId = DocumentsContract.getTreeDocumentId(treeUri);
			String parts[] = docId.split(":");
			if( parts.length < 1 ) return null;
			String volume = parts[0];
			String rel = parts.length > 1 ? parts[1] : "";
			String base = null;
			if( "primary".equals(volume) )
			{
				base = Environment.getExternalStorageDirectory().getAbsolutePath();
			}
			else
			{
				File sd = new File("/storage/" + volume);
				if( sd.isDirectory() ) base = sd.getAbsolutePath();
			}
			if( base == null )
			{
				Log.i(TAG, "DataDirPicker: cannot map tree uri to a real path: " + treeUri);
				return null;
			}
			String path = rel.length() > 0 ? base + "/" + rel : base;
			Log.i(TAG, "DataDirPicker: resolved " + treeUri + " -> " + path);
			return path;
		}
		catch (Exception e)
		{
			Log.i(TAG, "DataDirPicker: cannot resolve tree uri " + treeUri + ": " + e);
			return null;
		}
	}

	// Moving existing game data into the new directory

	static boolean migrateData(final Context p, final String newDir)
	{
		try
		{
			String from = Globals.DataDir;
			if( from == null || from.length() == 0 )
			{
				from = Globals.DownloadToSdcard ?
						Settings.SdcardAppPath.get().bestPath(p) :
						p.getFilesDir().getAbsolutePath();
			}
			File src = new File(from);
			File dst = new File(newDir);
			if( src.equals(dst) ) return true;
			if( !dst.isDirectory() && !dst.mkdirs() ) return false;

			if( src.isDirectory() )
			{
				File files[] = src.listFiles();
				if( files != null )
				{
					for( File f: files )
					{
						copyRecursive(f, new File(dst, f.getName()));
					}
				}
			}
			try
			{
				new FileOutputStream(new File(dst, ".nomedia")).close();
			}
			catch (Exception e) {}
			Log.i(TAG, "DataDirPicker: migrated game data from " + from + " to " + newDir);
			return true;
		}
		catch (Exception e)
		{
			Log.i(TAG, "DataDirPicker: migrate failed: " + e);
			return false;
		}
	}

	private static void copyRecursive(final File src, final File dst) throws IOException
	{
		if( src.isDirectory() )
		{
			if( !dst.isDirectory() && !dst.mkdirs() ) throw new IOException("cannot create directory " + dst);
			File files[] = src.listFiles();
			if( files != null )
			{
				for( File f: files )
				{
					copyRecursive(f, new File(dst, f.getName()));
				}
			}
		}
		else if( src.isFile() )
		{
			if( dst.exists() && dst.length() == src.length() ) return; // Already copied
			InputStream in = new FileInputStream(src);
			try
			{
				OutputStream out = new FileOutputStream(dst);
				try
				{
					byte buf[] = new byte[65536];
					int n;
					while( (n = in.read(buf)) > 0 ) out.write(buf, 0, n);
				}
				finally { out.close(); }
			}
			finally { in.close(); }
		}
	}
}
