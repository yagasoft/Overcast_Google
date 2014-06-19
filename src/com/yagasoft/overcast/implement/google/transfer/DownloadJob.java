/*
 * Copyright (C) 2011-2014 by Ahmed Osama el-Sawalhy
 *
 *		The Modified MIT Licence (GPL v3 compatible)
 * 			License terms are in a separate file (LICENCE.md)
 *
 *		Project/File: Overcast/com.yagasoft.overcast.implement.google/DownloadJob.java
 *
 *			Modified: Apr 15, 2014 (1:53:38 PM)
 *			   Using: Eclipse J-EE / JDK 7 / Windows 8.1 x64
 */

package com.yagasoft.overcast.implement.google.transfer;


import java.io.IOException;
import java.io.OutputStream;

import com.google.api.client.googleapis.media.MediaHttpDownloader;
import com.yagasoft.overcast.base.container.local.LocalFolder;
import com.yagasoft.overcast.base.container.transfer.event.TransferState;
import com.yagasoft.overcast.implement.google.container.RemoteFile;


/**
 * @see com.yagasoft.overcast.base.container.transfer.DownloadJob
 */
public class DownloadJob extends com.yagasoft.overcast.base.container.transfer.DownloadJob<MediaHttpDownloader>
{
	OutputStream canceller;

	/**
	 * Instantiates a new download job.
	 *
	 * @param remoteFile
	 *            Remote file.
	 * @param parent
	 *            Parent.
	 * @param overwrite
	 *            Overwrite.
	 * @param cspTransferer
	 *            Csp transferer.
	 */
	public DownloadJob(RemoteFile remoteFile, LocalFolder parent, boolean overwrite
			, MediaHttpDownloader cspTransferer)
	{
		super(remoteFile, parent, overwrite, cspTransferer);
	}

	@Override
	public void cancelTransfer() throws UnsupportedOperationException
	{
		try
		{
			canceller.close();
		}
		catch (IOException e)		// intended!
		{}

		notifyProgressListeners(TransferState.CANCELLED, 0);
	}

	/**
	 * @return the canceller
	 */
	public OutputStream getCanceller()
	{
		return canceller;
	}


	/**
	 * @param canceller the canceller to set
	 */
	public void setCanceller(OutputStream canceller)
	{
		this.canceller = canceller;
	}

}
