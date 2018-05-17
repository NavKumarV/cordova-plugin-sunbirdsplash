package org.sunbird.util;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;

import org.ekstep.genieservices.GenieService;
import org.ekstep.genieservices.commons.IResponseHandler;
import org.ekstep.genieservices.commons.bean.ContentImportResponse;
import org.ekstep.genieservices.commons.bean.EcarImportRequest;
import org.ekstep.genieservices.commons.bean.GenieResponse;
import org.ekstep.genieservices.commons.bean.enums.ContentImportStatus;
import org.ekstep.genieservices.commons.utils.CollectionUtil;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.List;

public final class ImportExportUtil {

  /**
   * Initiate the import file if Genie supported the file for side loading.
   *
   * @param intent
   * @return
   */
  public static boolean initiateImportFile(Activity activity, IImport callback, Intent intent, boolean showProgressDialog) {
    Uri uri = intent.getData();

    if (uri == null) {
      return false;
    }

    if (intent.getScheme().equals("content")) {
      return importGenieSupportedFile(activity, callback, getAttachmentFilePath(activity.getApplicationContext(), uri), true, showProgressDialog);
    } else if (intent.getScheme().equals("file")) {
      return importGenieSupportedFile(activity, callback, uri.getPath(), false, showProgressDialog);
    } else {
      return false;
    }

  }

  private static boolean importGenieSupportedFile(Activity activity, final IImport delegate, final String filePath, final boolean isAttachment, boolean showProgressDialog) {
    String extension;

    if (filePath.lastIndexOf(".") != -1 && filePath.lastIndexOf(".") != 0) {
      extension = filePath.substring(filePath.lastIndexOf(".") + 1);
    } else {
      extension =  "";
    }

    if (!extension.equalsIgnoreCase("ecar")) {
      return false;
    }


    if (extension.equalsIgnoreCase("ecar")) {
      importContent(activity, filePath, new ImportExportUtil.IImport() {
        @Override
        public void onImportSuccess() {

          if (isAttachment) {
            File file = new File(filePath);
            file.delete();
          }

          delegate.onImportSuccess();

        }

        @Override
        public void onImportFailure(ContentImportStatus status) {
          delegate.onImportFailure(status);
        }

        @Override
        public void onOutDatedEcarFound() {
          delegate.onOutDatedEcarFound();
        }
      });
    }

    return true;
  }

  private static String getAttachmentFilePath(Context context, Uri uri) {

    InputStream is = null;
    FileOutputStream os = null;
    String fullPath = null;
    String name = null;

    try {
      Cursor cursor = context.getContentResolver().query(uri, new String[]{MediaStore.MediaColumns.DISPLAY_NAME}, null, null, null);
      cursor.moveToFirst();
      int nameIndex = cursor.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME);
      if (nameIndex >= 0) {
        name = cursor.getString(nameIndex);
      }
      fullPath = Environment.getExternalStorageDirectory() + "/" + name;
      is = context.getContentResolver().openInputStream(uri);
      os = new FileOutputStream(fullPath);

      byte[] buffer = new byte[4096];
      int count;
      while ((count = is.read(buffer)) > 0) {
        os.write(buffer, 0, count);
      }
      os.close();
      is.close();
    } catch (Exception e) {
      if (is != null) {
        try {
          is.close();
        } catch (Exception e1) {
          e1.printStackTrace();
        }
      }
      if (os != null) {
        try {
          os.close();
        } catch (Exception e1) {
          e1.printStackTrace();
        }
      }
      if (fullPath != null) {
        File f = new File(fullPath);
        f.delete();
      }
    }

    return fullPath;
  }

  private static void importContent(Activity activity, String filePath, IImport iImport) {
    EcarImportRequest.Builder builder = new EcarImportRequest.Builder();
    builder.fromFilePath(filePath);

    builder.toFolder("/storage/emulated/0/Android/data/org.sunbird.app/files");
    GenieService.getAsyncService().getContentService().importEcar(builder.build(), new IResponseHandler<List<ContentImportResponse>>() {
      @Override
      public void onSuccess(GenieResponse<List<ContentImportResponse>> genieResponse) {

        List<ContentImportResponse> contentImportResponseList = genieResponse.getResult();
        if (!CollectionUtil.isNullOrEmpty(contentImportResponseList)) {
          ContentImportStatus importStatus = contentImportResponseList.get(0).getStatus();
          switch (importStatus) {
            case NOT_COMPATIBLE:
              iImport.onImportFailure(ContentImportStatus.NOT_COMPATIBLE);
              break;
            case CONTENT_EXPIRED:
              iImport.onImportFailure(ContentImportStatus.CONTENT_EXPIRED);
              break;
            case ALREADY_EXIST:
              iImport.onImportFailure(ContentImportStatus.ALREADY_EXIST);
              break;
            default:
              iImport.onImportSuccess();
              break;

          }
        } else {
          iImport.onImportSuccess();
        }
      }

      @Override
      public void onError(GenieResponse<List<ContentImportResponse>> genieResponse) {
        iImport.onImportFailure(ContentImportStatus.IMPORT_FAILED);
      }
    });
  }

  public interface IImport {
    void onImportSuccess();

    void onImportFailure(ContentImportStatus status);

    void onOutDatedEcarFound();
  }
}
