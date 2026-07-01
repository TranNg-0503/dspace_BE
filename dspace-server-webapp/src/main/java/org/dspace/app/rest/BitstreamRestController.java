  /**
   * The contents of this file are subject to the license and copyright
   * detailed in the LICENSE and NOTICE files at the root of the source
   * tree and available online at
   *
   * http://www.dspace.org/license/
   */
  package org.dspace.app.rest;

  import java.io.ByteArrayInputStream;
  import java.io.ByteArrayOutputStream;
  import java.io.IOException;
  import java.io.InputStream;
  import java.sql.SQLException;
  import java.text.ParseException;
  import java.time.ZoneId;
  import java.time.ZonedDateTime;
  import java.time.format.DateTimeFormatter;
  import java.util.List;
  import java.util.UUID;

  import javax.servlet.http.HttpServletRequest;
  import javax.servlet.http.HttpServletResponse;
  import javax.ws.rs.core.Response;

  import org.apache.catalina.connector.ClientAbortException;
  import org.apache.commons.collections4.ListUtils;
  import org.apache.commons.lang3.StringUtils;
  import org.apache.logging.log4j.Logger;
  import org.apache.pdfbox.pdmodel.PDDocument;
  import org.dspace.app.rest.converter.ConverterService;
  import org.dspace.app.rest.exception.DSpaceBadRequestException;
  import org.dspace.app.rest.model.BitstreamRest;
  import org.dspace.app.rest.model.hateoas.BitstreamResource;
  import org.dspace.app.rest.utils.ContextUtil;
  import static org.dspace.app.rest.utils.ContextUtil.obtainContext;
  import org.dspace.app.rest.utils.HttpHeadersInitializer;
  import static org.dspace.app.rest.utils.RegexUtils.REGEX_REQUESTMAPPING_IDENTIFIER_AS_UUID;
  import org.dspace.app.rest.utils.Utils;
  import org.dspace.app.rest.vnpay.VnpayPaymentService;
  import org.dspace.app.rest.vnpay.VnpayTransaction;
  import org.dspace.app.rest.vnpay.VnpayTransactionStatus;
  import org.dspace.authorize.AuthorizeException;
import org.dspace.authorize.service.AuthorizeService;
import org.dspace.content.Bitstream;
  import org.dspace.content.BitstreamFormat;
  import org.dspace.content.Bundle;
  import org.dspace.content.Item;
  import org.dspace.content.service.BitstreamFormatService;
  import org.dspace.content.service.BitstreamService;
  import org.dspace.content.service.ItemService;
  import org.dspace.core.Context;
  import org.dspace.disseminate.service.CitationDocumentService;
  import org.dspace.eperson.EPerson;
  import org.dspace.services.ConfigurationService;
  import org.dspace.services.EventService;
  import org.dspace.usage.UsageEvent;
  import org.dspace.util.PdfBoxUtils;
  import org.springframework.beans.factory.annotation.Autowired;
  import org.springframework.core.io.InputStreamResource;
  import org.springframework.data.rest.webmvc.ResourceNotFoundException;
  import org.springframework.http.HttpHeaders;
  import org.springframework.http.ResponseEntity;
  import org.springframework.security.access.prepost.PostAuthorize;
  import org.springframework.security.access.prepost.PreAuthorize;
  import org.springframework.web.bind.annotation.PathVariable;
  import org.springframework.web.bind.annotation.RequestMapping;
  import org.springframework.web.bind.annotation.RequestMethod;
  import static org.springframework.web.bind.annotation.RequestMethod.PUT;
  import org.springframework.web.bind.annotation.RestController;

  import com.nimbusds.jwt.JWTClaimsSet;
  import com.nimbusds.jwt.SignedJWT;
  /**
   * This is a specialized controller to provide access to the bitstream binary
   * content
   *
   * The mapping for requested endpoint try to resolve a valid UUID, for example
   * 
   * <pre>
   * {@code
   * https://<dspace.server.url>/api/core/bitstreams/26453b4d-e513-44e8-8d5b-395f62972eff/content
   * }
   * </pre>
   *
   * @author Andrea Bollini (andrea.bollini at 4science.it)
   * @author Tom Desair (tom dot desair at atmire dot com)
   * @author Frederic Van Reet (frederic dot vanreet at atmire dot com)
   */
  @RestController
  @RequestMapping("/api/" + BitstreamRest.CATEGORY + "/" + BitstreamRest.PLURAL_NAME
      + REGEX_REQUESTMAPPING_IDENTIFIER_AS_UUID)
  public class BitstreamRestController {

    private static final Logger log = org.apache.logging.log4j.LogManager
        .getLogger(BitstreamRestController.class);

    // Most file systems are configured to use block sizes of 4096 or 8192 and our
    // buffer should be a multiple of that.
    private static final int BUFFER_SIZE = 4096 * 10;

    @Autowired
    private BitstreamService bitstreamService;
    @Autowired
    private AuthorizeService authorizeService;

    @Autowired
    BitstreamFormatService bitstreamFormatService;

    @Autowired
    private EventService eventService;
    
    @Autowired
    private ItemService itemService;

    @Autowired
    private CitationDocumentService citationDocumentService;

    @Autowired
    private ConfigurationService configurationService;

    @Autowired
    ConverterService converter;

    @Autowired
    private VnpayPaymentService vnpayPaymentService;

    @Autowired
    Utils utils;

    @PreAuthorize("hasPermission(#uuid, 'BITSTREAM', 'READ')")
    @RequestMapping(method = { RequestMethod.GET, RequestMethod.HEAD }, value = "content")
    public ResponseEntity retrieve(@PathVariable UUID uuid, HttpServletResponse response,
        HttpServletRequest request) throws IOException, SQLException, AuthorizeException {

      Context context = ContextUtil.obtainContext(request);
      try {
        if (!isDownloadAuthorized(request, uuid, context)) {
          response.sendError(HttpServletResponse.SC_FORBIDDEN, "Payment required or invalid transaction");
          return null;
        }


        Bitstream bit = bitstreamService.find(context, uuid);

        String publisherName = "test";

        try {
            List<Bundle> bundles = bit.getBundles();

            log.info("Bundle count = {}", bundles.size());

            for (Bundle bundle : bundles) {

                log.info("Bundle name = {}", bundle.getName());

                try {
                    log.info("Item count = {}", bundle.getItems().size());
                } catch (Exception e) {
                    log.error("Cannot get items from bundle", e);
                }

                if (!bundle.getItems().isEmpty()) {

                    Item item = bundle.getItems().get(0);

                    log.info("Item ID = {}", item.getID());

                    String publisher = itemService.getMetadataFirstValue(
                            item,
                            "dc",
                            "publisher",
                            null,
                            Item.ANY
                    );

                    log.info("Publisher = {}", publisher);

                    if (StringUtils.isNotBlank(publisher)) {
                        publisherName = publisher;
                        break;
                    }
                }
            }

        } catch (Exception e) {
            log.error("Cannot resolve publisher", e);
        }


        EPerson currentUser = context.getCurrentUser();

        if (bit == null) {
          response.sendError(HttpServletResponse.SC_NOT_FOUND);
          return null;
        }

        Long lastModified = bitstreamService.getLastModified(bit);
        BitstreamFormat format = bit.getFormat(context);
        String mimetype = format.getMIMEType();
        String name = getBitstreamName(bit, format);

        if (StringUtils.isBlank(request.getHeader("Range"))) {
          // We only log a download request when serving a request without Range header.
          // This is because
          // a browser always sends a regular request first to check for Range support.
          eventService.fireEvent(
              new UsageEvent(
                  UsageEvent.Action.VIEW,
                  request,
                  context,
                  bit));
        }

        try {
          long filesize = bit.getSizeBytes();
          Boolean citationEnabledForBitstream = citationDocumentService.isCitationEnabledForBitstream(bit, context);

          var bitstreamResource = new org.dspace.app.rest.utils.BitstreamResource(name, uuid,
              currentUser != null ? currentUser.getID() : null,
              context.getSpecialGroupUuids(), citationEnabledForBitstream);

          HttpHeadersInitializer httpHeadersInitializer = new HttpHeadersInitializer()
              .withBufferSize(BUFFER_SIZE)
              .withFileName(name)
              .withChecksum(bitstreamResource.getChecksum())
              .withLength(bitstreamResource.contentLength())
              .withMimetype(mimetype)
              .with(request)
              .with(response);

          if (lastModified != null) {
            httpHeadersInitializer.withLastModified(lastModified);
          }

          // Determine if we need to send the file as a download or if the browser can
          // open it inline
          // The file will be downloaded if its size is larger than the configured
          // threshold,
          // or if its mimetype/extension appears in the
          // "webui.content_disposition_format" config
          long dispositionThreshold = configurationService.getLongProperty("webui.content_disposition_threshold");
          if ((dispositionThreshold >= 0 && filesize > dispositionThreshold)
              || checkFormatForContentDisposition(format)) {
            httpHeadersInitializer.withDisposition(HttpHeadersInitializer.CONTENT_DISPOSITION_ATTACHMENT);
          }

          // We have all the data we need, close the connection to the database so that it
          // doesn't stay open during
          // download/streaming
          

          // Send the data
          if (httpHeadersInitializer.isValid()) {
            HttpHeaders httpHeaders = httpHeadersInitializer.initialiseHeaders();

            if (RequestMethod.HEAD.name().equals(request.getMethod())) {
              log.debug("HEAD request - no response body");
              return ResponseEntity.ok().headers(httpHeaders).build();
            }

            try {
              boolean isPDF = mimetype.equals("application/pdf");
              // Only process PDFs
              if (isPDF) {
                PDDocument document = PDDocument.load(bitstreamResource.getInputStream());

                if (document != null && document.getNumberOfPages() > 0) {
                  String waterMarkText = getUserWatermarkText(request.getParameter("authentication-token"));
                  
                  String timeStamp = "Authorized licensed use limited to: " + publisherName;
                  
                  InputStream hiddenWatermarkedFile = PdfBoxUtils.addHiddenWatermark(document,
                      waterMarkText, timeStamp, waterMarkText);
                  InputStreamResource inputStreamResource = new InputStreamResource(hiddenWatermarkedFile);
                  document.close();
                  context.complete();
                  httpHeadersInitializer.withLength(hiddenWatermarkedFile.available());
                  HttpHeaders newHeaders = httpHeadersInitializer.initialiseHeaders();

                  return ResponseEntity.ok().headers(newHeaders).body(inputStreamResource);
                }
              }

              // Not a PDF or invalid PDF, return original
              return ResponseEntity.ok().headers(httpHeaders).body(bitstreamResource);

            } catch (ClientAbortException ex) {
              log.debug("Client aborted the request before the download was completed. " +
                  "Client is probably switching to a Range request.", ex);
            } catch (Exception e) {
              log.error("Error processing bitstream, returning original", e);
              // Return original bitstream if processing fails
              return ResponseEntity.ok().headers(httpHeaders).body(bitstreamResource);
            }
          }
        } catch (ClientAbortException ex) {
          System.out.println("Error processing bitstream, throw error in catch ClientAbortException" + ex.getMessage());

          log.debug("Client aborted the request before the download was completed. " +
              "Client is probably switching to a Range request.", ex);
        } catch (Exception e) {
          System.out.println("Error processing bitstream, throw error in catch Exception" + e.getMessage());
          throw new RuntimeException(e);
        }

        return null;
      } finally {
        if (context != null && context.isValid()) {
          context.complete();
        }
      }
    }

    private String getBitstreamName(Bitstream bit, BitstreamFormat format) {
      String name = bit.getName();
      if (name == null) {
        // give a default name to the file based on the UUID and the primary extension
        // of the format
        name = bit.getID().toString();
        if (format != null && format.getExtensions() != null && format.getExtensions().size() > 0) {
          name += "." + format.getExtensions().get(0);
        }
      }
      return name;
    }

    private boolean isNotAnErrorResponse(HttpServletResponse response) {
      Response.Status.Family responseCode = Response.Status.Family.familyOf(response.getStatus());
      return responseCode.equals(Response.Status.Family.SUCCESSFUL)
          || responseCode.equals(Response.Status.Family.REDIRECTION);
    }

    /**
     * Check if a Bitstream of the specified format should always be downloaded
     * (i.e. "content-disposition: attachment")
     * or can be opened inline (i.e. "content-disposition: inline").
     * <P>
     * NOTE that downloading via "attachment" is more secure, as the user's browser
     * will not attempt to process or
     * display the file. But, downloading via "inline" may be seen as more
     * user-friendly for common formats.
     * 
     * @param format BitstreamFormat
     * @return true if always download ("attachment"). false if can be opened inline
     *         ("inline")
     */
    private boolean checkFormatForContentDisposition(BitstreamFormat format) {
      // Undefined or Unknown formats should ALWAYS be downloaded for additional
      // security.
      if (format == null || format.getSupportLevel() == BitstreamFormat.UNKNOWN) {
        return true;
      }

      // Load additional formats configured to require download
      List<String> configuredFormats = List.of(configurationService.getArrayProperty("webui.content_disposition_format"));

      // If configuration includes "*", then all formats will always be downloaded.
      if (configuredFormats.contains("*")) {
        return true;
      }

      // Define a download list of formats which DSpace forces to ALWAYS be
      // downloaded.
      // These formats can embed JavaScript which may be run in the user's browser if
      // the file is opened inline.
      // Therefore, DSpace blocks opening these formats inline as it could be used for
      // an XSS attack.
      List<String> downloadOnlyFormats = List.of("text/html", "text/javascript", "text/xml", "rdf");

      // Combine our two lists
      List<String> formats = ListUtils.union(downloadOnlyFormats, configuredFormats);

      // See if the passed in format's MIME type or file extension is listed.
      boolean download = formats.contains(format.getMIMEType());
      if (!download) {
        for (String ext : format.getExtensions()) {
          if (formats.contains(ext)) {
            download = true;
            break;
          }
        }
      }
      return download;
    }

    /**
     * This method will update the bitstream format of the bitstream that
     * corresponds to the provided bitstream uuid.
     *
     * @param uuid    The UUID of the bitstream for which to update the bitstream
     *                format
     * @param request The request object
     * @return The wrapped resource containing the bitstream which in turn contains
     *         the bitstream format
     * @throws SQLException If something goes wrong in the database
     */
    @RequestMapping(method = PUT, consumes = { "text/uri-list" }, value = "format")
    @PreAuthorize("hasPermission(#uuid, 'BITSTREAM','WRITE')")
    @PostAuthorize("returnObject != null")
    public BitstreamResource updateBitstreamFormat(@PathVariable UUID uuid,
        HttpServletRequest request) throws SQLException {

      Context context = obtainContext(request);

      List<BitstreamFormat> bitstreamFormats = utils.constructBitstreamFormatList(request, context);

      if (bitstreamFormats.size() > 1) {
        throw new DSpaceBadRequestException("Only one bitstream format is allowed");
      }

      BitstreamFormat bitstreamFormat = bitstreamFormats.stream().findFirst()
          .orElseThrow(() -> new DSpaceBadRequestException("No valid bitstream format was provided"));

      Bitstream bitstream = bitstreamService.find(context, uuid);

      if (bitstream == null) {
        throw new ResourceNotFoundException("Bitstream with id: " + uuid + " not found");
      }

      bitstream.setFormat(context, bitstreamFormat);

      context.commit();

      BitstreamRest bitstreamRest = converter.toRest(context.reloadEntity(bitstream), utils.obtainProjection());
      return converter.toResource(bitstreamRest);
    }

    private String getUserWatermarkText(String token) {
      String timeStamp = ZonedDateTime.now(ZoneId.of("Asia/Ho_Chi_Minh"))
          .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

      if (token == null) {
        return "Downloaded by: Anonymous on " + timeStamp;
      }

      try {
        SignedJWT signedJWT = SignedJWT.parse(token);
        JWTClaimsSet claims = signedJWT.getJWTClaimsSet();
        String userEmail = claims.getStringClaim("email");

        return "Downloaded by: " + userEmail + " on " + timeStamp;
      } catch (ParseException e) {
        return "Downloaded by: Anonymous on " + timeStamp;
      }
    }

    @PreAuthorize("hasPermission(#uuid, 'BITSTREAM', 'READ')")
    @RequestMapping(method = { RequestMethod.GET, RequestMethod.HEAD }, value = "preview")
    public ResponseEntity retrievePreview(@PathVariable UUID uuid, HttpServletResponse response,
        HttpServletRequest request) throws IOException, SQLException, AuthorizeException {

      Context context = ContextUtil.obtainContext(request);

      Bitstream bit = bitstreamService.find(context, uuid);
      EPerson currentUser = context.getCurrentUser();
      boolean isAdmin = authorizeService.isAdmin(context);

      if (bit == null) {
        response.sendError(HttpServletResponse.SC_NOT_FOUND);
        return null;
      }

      Long lastModified = bitstreamService.getLastModified(bit);
      BitstreamFormat format = bit.getFormat(context);
      String mimetype = format.getMIMEType();
      String name = getBitstreamName(bit, format);

      if (StringUtils.isBlank(request.getHeader("Range"))) {
        // We only log a download request when serving a request without Range header.
        // This is because
        // a browser always sends a regular request first to check for Range support.
        eventService.fireEvent(
            new UsageEvent(
                UsageEvent.Action.VIEW,
                request,
                context,
                bit));
      }

      try {
        long filesize = bit.getSizeBytes();
        Boolean citationEnabledForBitstream = citationDocumentService.isCitationEnabledForBitstream(bit, context);

        var bitstreamResource = new org.dspace.app.rest.utils.BitstreamResource(name, uuid,
            currentUser != null ? currentUser.getID() : null,
            context.getSpecialGroupUuids(), citationEnabledForBitstream);

        HttpHeadersInitializer httpHeadersInitializer = new HttpHeadersInitializer()
            .withBufferSize(BUFFER_SIZE)
            .withFileName(name)
            .withChecksum(bitstreamResource.getChecksum())
            .withLength(bitstreamResource.contentLength())
            .withMimetype(mimetype)
            .with(request)
            .with(response);

        if (lastModified != null) {
          httpHeadersInitializer.withLastModified(lastModified);
        }

        // Determine if we need to send the file as a download or if the browser can
        // open it inline
        // The file will be downloaded if its size is larger than the configured
        // threshold,
        // or if its mimetype/extension appears in the
        // "webui.content_disposition_format" config
        long dispositionThreshold = configurationService.getLongProperty("webui.content_disposition_threshold");
        if ((dispositionThreshold >= 0 && filesize > dispositionThreshold)
            || checkFormatForContentDisposition(format)) {
          httpHeadersInitializer.withDisposition(HttpHeadersInitializer.CONTENT_DISPOSITION_ATTACHMENT);
        }

        // We have all the data we need, close the connection to the database so that it
        // doesn't stay open during
        // download/streaming
        context.complete();

        // Send the data
        if (httpHeadersInitializer.isValid()) {
          HttpHeaders httpHeaders = httpHeadersInitializer.initialiseHeaders();

          if (RequestMethod.HEAD.name().equals(request.getMethod())) {
            log.debug("HEAD request - no response body");
            return ResponseEntity.ok().headers(httpHeaders).build();
          }

          PDDocument originalDocument = PDDocument.load(bitstreamResource.getInputStream());

          PDDocument document;

          if (isAdmin) {
              // Admin xem toàn bộ PDF
              document = originalDocument;
          } else {

              int pageCount = originalDocument.getNumberOfPages();

              if (pageCount < 5) {
                  originalDocument.close();
                  return ResponseEntity.status(400).body("File must have at least 5 pages to preview");
              }

              document = getPreviewDocument(originalDocument);
          }

          // --- Bỏ watermark ---
          ByteArrayOutputStream baos = new ByteArrayOutputStream();
          document.save(baos); // Lưu PDF vào output stream
          InputStream originalStream = new ByteArrayInputStream(baos.toByteArray());
          InputStreamResource inputStreamResource = new InputStreamResource(originalStream);

          if (document != originalDocument) {
              originalDocument.close();
          }
          document.close();

          // Update the content length
          httpHeadersInitializer.withLength(originalStream.available());
          HttpHeaders newHeaders = httpHeadersInitializer.initialiseHeaders();

          return ResponseEntity.ok().headers(newHeaders).body(inputStreamResource);
        }

      } catch (ClientAbortException ex) {
        log.debug("Client aborted the request before the download was completed. " +
            "Client is probably switching to a Range request.", ex);
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
      return null;
    }

    private PDDocument getPreviewDocument(PDDocument document) {
      int pageCount = document.getNumberOfPages();
      int pageToRender = (int) Math.min(5, Math.ceil(pageCount / 5.0));
      PDDocument previewDocument = new PDDocument();
      for (int i = 0; i < pageToRender; i++) {
        previewDocument.addPage(document.getPage(i));
      }
      return previewDocument;
    }

    private boolean isDownloadAuthorized(HttpServletRequest request, UUID uuid, Context context) {
      String transactionId = request.getParameter("transactionId");
      if (StringUtils.isBlank(transactionId)) {
        return true;
      }

      try {
        VnpayTransaction transaction = vnpayPaymentService.getTransaction(context, transactionId);
        if (transaction == null) {
          return false;
        }
        return transaction.getStatus() == VnpayTransactionStatus.SUCCESS
            && uuid.toString().equals(transaction.getBitstreamId());
      } catch (Exception e) {
        log.error("Unable to validate VNPAY transaction for download", e);
        return false;
      }
    }
  }