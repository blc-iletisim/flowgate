/**
 * Copyright 2019 VMware, Inc.
 * SPDX-License-Identifier: BSD-2-Clause
*/
package com.vmware.wormhole.service;

import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;

import javax.net.ssl.SSLException;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;

import com.vmware.ops.api.client.exceptions.AuthException;
import com.vmware.vim.binding.vim.fault.InvalidLocale;
import com.vmware.vim.binding.vim.fault.InvalidLogin;
import com.vmware.vim.vmomi.client.exception.ConnectionException;
import com.vmware.vim.vmomi.client.exception.SslException;
import com.vmware.wormhole.auth.AuthVcUser;
import com.vmware.wormhole.auth.NlyteAuth;
import com.vmware.wormhole.auth.PowerIQAuth;
import com.vmware.wormhole.common.model.FacilitySoftwareConfig;
import com.vmware.wormhole.common.model.SDDCSoftwareConfig;
import com.vmware.wormhole.exception.WormholeRequestException;
import com.vmware.wormhole.verifycert.VRopsAuth;

@Component
public class ServerValidationService {
   public void validVCServer(SDDCSoftwareConfig server) {
      AuthVcUser authVcUser = getVcAuth(server);
      try {
         
         authVcUser.authenticateUser(server.getUserName(), server.getPassword());
         
      }catch(SslException | SSLException e) {
         throw new WormholeRequestException(HttpStatus.BAD_REQUEST,"Certificate verification error",e.getCause());
      }catch(AuthException | InvalidLogin e) {
         throw new WormholeRequestException(HttpStatus.BAD_REQUEST,"Invalid user name or password",e.getCause());
      }catch(ConnectionException | URISyntaxException | InvalidLocale e) {
         throw new WormholeRequestException(HttpStatus.BAD_REQUEST,"Failed to connect to server",e.getCause());
      }
   }

   public AuthVcUser getVcAuth(SDDCSoftwareConfig server) {
      return new AuthVcUser(server.getServerURL(),443, !server.isVerifyCert());
   }

   public void validateVROServer(SDDCSoftwareConfig server) {
      try {
            VRopsAuth ss = createVRopsAuth(server);
            ss.getClient().apiVersionsClient().getCurrentVersion();
      }catch(Exception e) {
         if(e instanceof SSLException) {
            throw new WormholeRequestException(HttpStatus.BAD_REQUEST,"Certificate verification error",e.getCause());
         }else if(e instanceof AuthException) {
            throw new WormholeRequestException(HttpStatus.UNAUTHORIZED,"Invalid user name or password",e.getCause());
         }else if(e instanceof ConnectionException){
            throw new WormholeRequestException(HttpStatus.BAD_REQUEST,"Failed to connect to server",e.getCause());
         }else {
            throw new WormholeRequestException("This is a Exception Message :"+e.getMessage(),e.getCause());
         }
      }
   }

   public VRopsAuth createVRopsAuth(SDDCSoftwareConfig server) {
      return new VRopsAuth(server);
   }


   public void validateFacilityServer(FacilitySoftwareConfig config) throws WormholeRequestException{
      try {
         switch (config.getType()) {
         case Nlyte:
            NlyteAuth nlyteAuth = createNlyteAuth();
            if(!nlyteAuth.auth(config)) {
               throw new WormholeRequestException(HttpStatus.UNAUTHORIZED,
                     "Invalid user name or password", null);
            }
            break;
         case PowerIQ:
            PowerIQAuth powerIQAuth = createPowerIQAuth();
            if(!powerIQAuth.auth(config)) {
               throw new WormholeRequestException(HttpStatus.UNAUTHORIZED,
                     "Invalid user name or password", null);
            }
            break;
         case InfoBlox:
            break;
         case Device42:
            break;
         case OtherDCIM:
            break;
         case Labsdb:
            break;
         case OtherCMDB:
            break;
         default:
            throw WormholeRequestException.InvalidFiled("type", config.getType().toString());
         }
      }catch (ResourceAccessException e) {
         if(e.getCause() instanceof  SSLException) {
            throw new WormholeRequestException(HttpStatus.BAD_REQUEST,
                  "Certificate verification error", e.getCause(),WormholeRequestException.InvalidSSLCertificateCode);
         }else if(e.getCause() instanceof  UnknownHostException) {
            throw new WormholeRequestException(HttpStatus.BAD_REQUEST, "Unknown Host",
                  e.getCause(),WormholeRequestException.UnknownHostCode);
         }
         throw new WormholeRequestException(HttpStatus.BAD_REQUEST,
               e.getMessage(), e.getCause());
      } catch (UnknownHostException e) {
         throw new WormholeRequestException(HttpStatus.BAD_REQUEST, "Unknown Host",
               e.getCause(),WormholeRequestException.UnknownHostCode);
      } catch (SSLException | KeyManagementException
            | CertificateException | NoSuchAlgorithmException | KeyStoreException e) {
         throw new WormholeRequestException(HttpStatus.BAD_REQUEST,
               "Certificate verification error", e.getCause(),
               WormholeRequestException.InvalidSSLCertificateCode);
      }  catch (HttpClientErrorException e) {
         if(HttpStatus.UNAUTHORIZED.equals(e.getStatusCode())) {
            throw new WormholeRequestException(HttpStatus.UNAUTHORIZED,
                  "Invalid user name or password", e.getCause());
         }else if(HttpStatus.FORBIDDEN.equals(e.getStatusCode())) {
            throw new WormholeRequestException(HttpStatus.FORBIDDEN,
                  "403 Forbidden", e.getCause());
         }
      }
   }
   public NlyteAuth createNlyteAuth() {
      return new NlyteAuth();
   }

   public PowerIQAuth createPowerIQAuth() {
      return new PowerIQAuth();
   }
}