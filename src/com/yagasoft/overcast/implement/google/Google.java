/*
 * Copyright (C) 2011-2014 by Ahmed Osama el-Sawalhy
 *
 *		The Modified MIT Licence (GPL v3 compatible)
 * 			Licence terms are in a separate file (LICENCE.md)
 *
 *		Project/File: Overcast_Google/com.yagasoft.overcast.implement.google/Google.java
 *
 *			Modified: 23-Jun-2014 (20:35:08)
 *			   Using: Eclipse J-EE / JDK 8 / Windows 8.1 x64
 */

package com.yagasoft.overcast.implement.google;


import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.googleapis.media.MediaHttpDownloader;
import com.google.api.client.googleapis.media.MediaHttpDownloaderProgressListener;
import com.google.api.client.googleapis.media.MediaHttpUploader;
import com.google.api.client.googleapis.media.MediaHttpUploaderProgressListener;
import com.google.api.client.http.FileContent;
import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.model.About;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.ParentReference;
import com.yagasoft.logger.Logger;
import com.yagasoft.overcast.base.container.local.LocalFile;
import com.yagasoft.overcast.base.container.local.LocalFolder;
import com.yagasoft.overcast.base.container.operation.IOperationListener;
import com.yagasoft.overcast.base.container.operation.Operation;
import com.yagasoft.overcast.base.container.remote.RemoteFactory;
import com.yagasoft.overcast.base.container.transfer.event.TransferState;
import com.yagasoft.overcast.base.csp.CSP;
import com.yagasoft.overcast.exception.AuthorisationException;
import com.yagasoft.overcast.exception.CSPBuildException;
import com.yagasoft.overcast.exception.CreationException;
import com.yagasoft.overcast.exception.OperationException;
import com.yagasoft.overcast.exception.TransferException;
import com.yagasoft.overcast.implement.google.container.RemoteFile;
import com.yagasoft.overcast.implement.google.container.RemoteFolder;
import com.yagasoft.overcast.implement.google.transfer.DownloadJob;
import com.yagasoft.overcast.implement.google.transfer.UploadJob;


/**
 * Class representing Google Drive. It handles authentication, transfer of files, and contains the root.
 */
public class Google extends CSP<File, MediaHttpDownloader, Drive.Files.Insert> implements MediaHttpDownloaderProgressListener,
		MediaHttpUploaderProgressListener
{

	/** Constant: VERSION. */
	public static final String													VERSION				= "1.05.0100";

	/** The Google singleton. */
	static private Google														instance;

	/**
	 * Be sure to specify the name of your application. If the application name is {@code null} or blank, the application will log
	 * a warning.
	 * Suggested format is "MyCompany-ProductName/1.0".
	 */
	static final String															APPLICATION_NAME	= "Overcast";

	/** Global instance of the HTTP transport. */
	static HttpTransport														httpTransport;

	/** Global Drive API client. */
	public static Drive															driveService;

	/** Global instance of the JSON factory. */
	static final JsonFactory													JSON_FACTORY		= JacksonFactory
																											.getDefaultInstance();

	/** The authorisation object. */
	static Authorisation														authorisation;

	/** The remote file factory. */
	public static RemoteFactory<File, RemoteFolder, File, RemoteFile, Google>	factory;

	/**
	 * Instantiates a new Google instance.
	 *
	 * @param userID
	 *            the user id
	 * @throws CSPBuildException
	 *             the CSP build exception
	 * @throws AuthorisationException
	 *             the authorisation exception
	 */
	private Google(String userID) throws CSPBuildException, AuthorisationException
	{
		Logger.info("building google object");

		try
		{
			// used in authorisation and transfers.
			httpTransport = GoogleNetHttpTransport.newTrustedTransport();

			// authenticate.
			authorisation = new Authorisation(userID, "google");
			authorisation.authorise();

			// set up the global Drive instance
			driveService = new Drive.Builder(httpTransport, JSON_FACTORY, authorisation.credential)
					.setApplicationName(APPLICATION_NAME).build();

			// initialise the remote file factory.
			factory = new RemoteFactory<File, RemoteFolder, File, RemoteFile, Google>(
					this, RemoteFolder.class, RemoteFile.class, "/My Drive");

			name = "Google";

			Logger.info("done building google");
		}
		catch (IOException | GeneralSecurityException e)
		{
			Logger.error("failed in building google");

			e.printStackTrace();
			throw new CSPBuildException("Can't construct CSP object! " + e.getMessage());
		}
	}

	/**
	 * Gets the single instance of Google.
	 *
	 * @param userID
	 *            User id.
	 * @return single instance of Google
	 * @throws CSPBuildException
	 *             the CSP build exception
	 * @throws AuthorisationException
	 *             the authorisation exception
	 */
	public static Google getInstance(String userID) throws CSPBuildException, AuthorisationException
	{
		if (instance == null)
		{
			instance = new Google(userID);
		}

		return instance;
	}

	/**
	 * Calls {@link #getInstance(String)}
	 */
	public static Google getInstance(String userId, String password) throws CSPBuildException, AuthorisationException
	{
		return getInstance(userId);
	}

	/**
	 * @see com.yagasoft.overcast.base.csp.CSP#destroyInstance()
	 */
	@Override
	public void destroyInstance()
	{
		instance = null;
	}

	/**
	 * @see com.yagasoft.overcast.base.csp.CSP#initTree(IContentListener)
	 */
	@Override
	public void initTree(IOperationListener listener) throws OperationException
	{
		if (remoteFileTree != null)
		{
			return;
		}

		try
		{
			remoteFileTree = factory.createFolder();
			remoteFileTree.setId("root");
			remoteFileTree.updateFromSource(false, false);

			if (listener != null)
			{
				remoteFileTree.addOperationListener(listener, Operation.ADD);
				remoteFileTree.addOperationListener(listener, Operation.REMOVE);
			}

			// buildFileTree(false);
		}
		catch (CreationException e)
		{
			Logger.error("can't initialise tree");
			Logger.except(e);
			e.printStackTrace();
		}
	}

	/**
	 * @see com.yagasoft.overcast.base.csp.CSP#calculateRemoteFreeSpace()
	 */
	@Override
	public long calculateRemoteFreeSpace() throws OperationException
	{
		Logger.info("getting google freespace");

		try
		{
			About about = driveService.about().get().execute();
			remoteFreeSpace = about.getQuotaBytesTotal() - about.getQuotaBytesUsed();

			Logger.info("got Google's free space");

			return remoteFreeSpace;
		}
		catch (IOException e)
		{
			Logger.error("failed to get free space: Google");
			Logger.except(e);
			e.printStackTrace();

			throw new OperationException("Couldn't get free space! " + e.getMessage());
		}
	}

	/**
	 * @see com.yagasoft.overcast.base.csp.CSP#initDownload(com.yagasoft.overcast.base.container.remote.RemoteFile,
	 *      com.yagasoft.overcast.base.container.local.LocalFolder, boolean)
	 */
	@Override
	protected DownloadJob initDownload(com.yagasoft.overcast.base.container.remote.RemoteFile<?> file, LocalFolder parent
			, boolean overwrite) throws TransferException
	{
		// initialise downloader.
		MediaHttpDownloader downloader = new MediaHttpDownloader(Google.getHttpTransport()
				, Google.driveService.getRequestFactory().getInitializer());
		downloader.setDirectDownloadEnabled(false);
		downloader.setProgressListener(this);
		downloader.setChunkSize(MediaHttpUploader.MINIMUM_CHUNK_SIZE);

		// create a download job.
		DownloadJob downloadJob = new DownloadJob((RemoteFile) file, parent, overwrite, downloader);

		return downloadJob;
	}

	/**
	 * @see com.yagasoft.overcast.base.csp.CSP#initiateDownload()
	 */
	@Override
	protected void initiateDownload() throws TransferException
	{
		try
		{
			// ... get a stream to the file on the local disk ...
			OutputStream out = new FileOutputStream(currentDownloadJob.getLocalFile().getSourceObject().toFile());

			// used to cancel the download
			((DownloadJob) currentDownloadJob).setCanceller(out);

			// ... start downloading ...
			currentDownloadJob.getCspTransferer().download(
					new GenericUrl(currentDownloadJob.getRemoteFile().getLink()), out);
			// close the file stream after download has finished.
			out.close();
			currentDownloadJob.success();
		}
		catch (IOException e)
		{
			// do nothing, probably because it's been cancelled
			if ( !e.getMessage().contains("Stream Closed"))
			{
				Logger.error("downloading: " + currentDownloadJob.getRemoteFile().getPath());
				Logger.except(e);
				e.printStackTrace();

				throw new TransferException("Failed to download file! " + e.getMessage());
			}
		}

	}

	/**
	 * @see com.google.api.client.googleapis.media.MediaHttpDownloaderProgressListener#progressChanged(com.google.api.client.googleapis.media.MediaHttpDownloader)
	 */
	@Override
	public void progressChanged(MediaHttpDownloader downloader) throws IOException
	{
		switch (downloader.getDownloadState())
		{
			case MEDIA_IN_PROGRESS:
				currentDownloadJob.progress((float) downloader.getProgress());		// notify listeners of progress.
				break;

			case MEDIA_COMPLETE:
			default:
				System.out.println(downloader.getDownloadState());
				break;
		}

	}

	/**
	 * @see com.yagasoft.overcast.base.csp.CSP#initUpload(com.yagasoft.overcast.base.container.local.LocalFile,
	 *      com.yagasoft.overcast.base.container.remote.RemoteFolder, boolean,
	 *      com.yagasoft.overcast.base.container.remote.RemoteFile)
	 */
	@Override
	protected UploadJob initUpload(LocalFile file, com.yagasoft.overcast.base.container.remote.RemoteFolder<?> parent
			, boolean overwrite, com.yagasoft.overcast.base.container.remote.RemoteFile<?> remoteFile) throws TransferException
	{
		// prepare file information to be passed to Google service.
		File metadata = new File();
		metadata.setTitle(file.getName());
		metadata.setMimeType(file.getType());
		metadata.setParents(Arrays.asList(new ParentReference().setId(parent.getId())));

		// file content object to be passed as well.
		FileContent content = new FileContent(file.getType(), file.getSourceObject().toFile());

		try
		{
			// pass info and content object, and create the uploader.
			Drive.Files.Insert insert = Google.driveService.files().insert(metadata, content);

			// initialise the uploader.
			MediaHttpUploader uploader = insert.getMediaHttpUploader();
			uploader.setDirectUploadEnabled(false);
			uploader.setProgressListener(this);
			uploader.setChunkSize(MediaHttpUploader.MINIMUM_CHUNK_SIZE);

			// create an upload job.
			UploadJob uploadJob = new UploadJob(file, (RemoteFile) remoteFile, (RemoteFolder) parent
					, overwrite, insert);

			return uploadJob;
		}
		catch (IOException e)
		{
			throw new TransferException(e.getMessage());
		}
	}

	@SuppressWarnings("unchecked")
	@Override
	protected void initiateUpload() throws TransferException
	{
		try
		{
			// Google doesn't support cancelling, so this is a workaround ...

			// runs the uploader itself in a different thread to be able to cancel it if needed.
			ExecutorService executor = Executors.newSingleThreadExecutor();

			Future<Void> future = (Future<Void>) executor.submit(() ->
			{
				try
				{
					currentUploadJob.success(currentUploadJob.getCspTransferer().execute());
				}
				catch (IOException e)
				{
					e.printStackTrace();
					throw new RuntimeException(e.getMessage());
				}
			});

			((UploadJob) currentUploadJob).setCanceller(future);

			future.get();			// run the thread.
			executor.shutdown();	// wait for it to finish.
		}
		catch (CancellationException e)		// intended! Will be thrown if the upload is cancelled.
		{}
		catch (RuntimeException | ExecutionException | InterruptedException e)	// thrown from inside the thread
		{
			Logger.error("uploading: " + currentUploadJob.getLocalFile().getPath());
			Logger.except(e);
			e.printStackTrace();

			// forward it
			throw new TransferException("Failed to upload file! " + e.getMessage());
		}
	}

	/**
	 * @see com.google.api.client.googleapis.media.MediaHttpUploaderProgressListener#progressChanged(com.google.api.client.googleapis.media.MediaHttpUploader)
	 */
	@Override
	public void progressChanged(MediaHttpUploader uploader) throws IOException
	{
		switch (uploader.getUploadState())
		{
			case INITIATION_COMPLETE:
				currentUploadJob.notifyProgressListeners(TransferState.INITIALISED, 0.0f);
				break;

			case MEDIA_IN_PROGRESS:
				currentUploadJob.progress((float) uploader.getProgress());
				break;

			case MEDIA_COMPLETE:
			default:
				System.out.println(uploader.getUploadState());
				break;
		}
	}

	@Override
	public com.yagasoft.overcast.base.container.remote.RemoteFactory<?, ?, ?, ?, ?> getAbstractFactory()
	{
		return factory;
	}

	// //////////////////////////////////////////////////////////////////////////////////////
	// #region Getters and setters.
	// ======================================================================================

	@Override
	public Authorisation getAuthorisation()
	{
		return Google.authorisation;
	}

	/**
	 * @return the httpTransport
	 */
	public static HttpTransport getHttpTransport()
	{
		return httpTransport;
	}

	/**
	 * @param httpTransport
	 *            the httpTransport to set
	 */
	public static void setHttpTransport(HttpTransport httpTransport)
	{
		Google.httpTransport = httpTransport;
	}

	/**
	 * @return the driveService
	 */
	public static Drive getDriveService()
	{
		return driveService;
	}

	/**
	 * @return the jsonFactory
	 */
	public static JsonFactory getJsonFactory()
	{
		return JSON_FACTORY;
	}

	/**
	 * @return the factory
	 */
	public static RemoteFactory<File, RemoteFolder, File, RemoteFile, Google> getFactory()
	{
		return factory;
	}

	// ======================================================================================
	// #endregion Getters and setters.
	// //////////////////////////////////////////////////////////////////////////////////////

}
