/**************************************************************************************************
 * Copyright (c) 2010 Fabian Steeg. All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0 which accompanies this
 * distribution, and is available at http://www.eclipse.org/legal/epl-v10.html
 * <p/>
 * Contributors: Fabian Steeg - initial API and implementation
 *************************************************************************************************/

package de.uni_koeln.ub.drc.ui.login;

import java.io.IOException;
import java.util.Map;

import javax.security.auth.Subject;
import javax.security.auth.callback.Callback;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.callback.NameCallback;
import javax.security.auth.callback.PasswordCallback;
import javax.security.auth.callback.UnsupportedCallbackException;
import javax.security.auth.login.LoginException;
import javax.security.auth.spi.LoginModule;

import de.uni_koeln.ub.drc.data.User;
import de.uni_koeln.ub.drc.ui.DrcUiActivator;
import de.uni_koeln.ub.drc.ui.Messages;

/**
 * Simple login module implementation.
 * @author Fabian Steeg (fsteeg)
 */
public final class SimpleLoginModule implements LoginModule {

  private CallbackHandler callbackHandler;
  private boolean loggedIn;
  private Subject subject;
  private User currentUser;

  /**
   * {@inheritDoc}
   * @see javax.security.auth.spi.LoginModule#initialize(javax.security.auth.Subject,
   *      javax.security.auth.callback.CallbackHandler, java.util.Map, java.util.Map)
   */
  @SuppressWarnings( "rawtypes" )
  /* from implemented API */
  @Override
  public void initialize(final Subject subject, final CallbackHandler callbackHandler,
      final Map sharedState, final Map options) {
    this.subject = subject;
    this.callbackHandler = callbackHandler;
  }

  /**
   * {@inheritDoc}
   * @see javax.security.auth.spi.LoginModule#login()
   */
  @Override
  public boolean login() throws LoginException {
    NameCallback userCallback = new NameCallback(Messages.User);
    PasswordCallback passCallback = new PasswordCallback(Messages.Password, false);
    try {
      callbackHandler.handle(new Callback[] {
      /* new TextOutputCallback(TextOutputCallback.INFORMATION, "Please login"), */userCallback,
          passCallback });
    } catch (IOException e) {
      e.printStackTrace();
    } catch (UnsupportedCallbackException e) {
      e.printStackTrace();
    }
    return authenticate(userCallback, passCallback);
  }

  private boolean authenticate(final NameCallback userCallback, final PasswordCallback passCallback) {
    String name = userCallback.getName();
    String pass = passCallback.getPassword() != null ? new String(passCallback.getPassword()) : ""; //$NON-NLS-1$
    User candidate = null;
    try {
      candidate = User.withId(DrcUiActivator.instance().userDb(), name);
    } catch (Throwable x) {
      x.printStackTrace();
    }
    if (validLogin(candidate, pass)) {
      currentUser = candidate;
      loggedIn = true;
      System.out.println("Logged in: " + currentUser); //$NON-NLS-1$
    }
    return loggedIn;
  }

  private boolean validLogin(User candidate, final String pass) {
    return candidate.pass().equals(pass);
  }

  /**
   * {@inheritDoc}
   * @see javax.security.auth.spi.LoginModule#commit()
   */
  @Override
  public boolean commit() throws LoginException {
    subject.getPublicCredentials()
        .add(
            String.format("%s " + "" + " %s (%s)", currentUser.name(), currentUser.region(), //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-2$
                currentUser.id()));
    subject.getPrivateCredentials().add(currentUser);
    return loggedIn;
  }

  /**
   * {@inheritDoc}
   * @see javax.security.auth.spi.LoginModule#abort()
   */
  @Override
  public boolean abort() throws LoginException {
    loggedIn = false;
    return true;
  }

  /**
   * {@inheritDoc}
   * @see javax.security.auth.spi.LoginModule#logout()
   */
  @Override
  public boolean logout() throws LoginException {
    loggedIn = false;
    return true;
  }
}
