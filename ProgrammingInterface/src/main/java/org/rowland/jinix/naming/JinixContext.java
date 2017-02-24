package org.rowland.jinix.naming;

import javax.naming.*;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.Hashtable;

/**
 * Naming Context for Jinix root namespace.
 */
public class JinixContext implements Context {

    NameSpace root;

    public JinixContext(NameSpace rootNameSpace) {
        root = rootNameSpace;
    }

    @Override
    public Object lookup(Name name) throws NamingException {
                throw new OperationNotSupportedException();
    }

    @Override
    public Object lookup(String name) throws NamingException {
        try {
            return root.lookup(name).remote;
        } catch (RemoteException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void bind(Name name, Object obj) throws NamingException {
                throw new OperationNotSupportedException();
    }

    @Override
    public void bind(String name, Object obj) throws NamingException {
        if (!(obj instanceof Remote)) {
            throw new IllegalArgumentException("Jinix only supports binding Remote objects");
        }
        try {
            root.bind(name, (Remote) obj);
        } catch (RemoteException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void rebind(Name name, Object obj) throws NamingException {
                throw new OperationNotSupportedException();
    }

    @Override
    public void rebind(String name, Object obj) throws NamingException {
                throw new OperationNotSupportedException();
    }

    @Override
    public void unbind(Name name) throws NamingException {
                throw new OperationNotSupportedException();
    }

    @Override
    public void unbind(String name) throws NamingException {
                throw new OperationNotSupportedException();
    }

    @Override
    public void rename(Name oldName, Name newName) throws NamingException {
                throw new OperationNotSupportedException();
    }

    @Override
    public void rename(String oldName, String newName) throws NamingException {
                throw new OperationNotSupportedException();
    }

    @Override
    public NamingEnumeration<NameClassPair> list(Name name) throws NamingException {
                throw new OperationNotSupportedException();
    }

    @Override
    public NamingEnumeration<NameClassPair> list(String name) throws NamingException {
                throw new OperationNotSupportedException();
    }

    @Override
    public NamingEnumeration<Binding> listBindings(Name name) throws NamingException {
                throw new OperationNotSupportedException();
    }

    @Override
    public NamingEnumeration<Binding> listBindings(String name) throws NamingException {
                throw new OperationNotSupportedException();
    }

    @Override
    public void destroySubcontext(Name name) throws NamingException {
                throw new OperationNotSupportedException();
    }

    @Override
    public void destroySubcontext(String name) throws NamingException {
                throw new OperationNotSupportedException();
    }

    @Override
    public Context createSubcontext(Name name) throws NamingException {
                throw new OperationNotSupportedException();
    }

    @Override
    public Context createSubcontext(String name) throws NamingException {
                throw new OperationNotSupportedException();
    }

    @Override
    public Object lookupLink(Name name) throws NamingException {
                throw new OperationNotSupportedException();
    }

    @Override
    public Object lookupLink(String name) throws NamingException {
                throw new OperationNotSupportedException();
    }

    @Override
    public NameParser getNameParser(Name name) throws NamingException {
                throw new OperationNotSupportedException();
    }

    @Override
    public NameParser getNameParser(String name) throws NamingException {
                throw new OperationNotSupportedException();
    }

    @Override
    public Name composeName(Name name, Name prefix) throws NamingException {
                throw new OperationNotSupportedException();
    }

    @Override
    public String composeName(String name, String prefix) throws NamingException {
                throw new OperationNotSupportedException();
    }

    @Override
    public Object addToEnvironment(String propName, Object propVal) throws NamingException {
        throw new OperationNotSupportedException();
    }

    @Override
    public Object removeFromEnvironment(String propName) throws NamingException {
        throw new OperationNotSupportedException();
    }

    @Override
    public Hashtable<?, ?> getEnvironment() throws NamingException {
        throw new OperationNotSupportedException();
    }

    @Override
    public void close() throws NamingException {

    }

    @Override
    public String getNameInNamespace() throws NamingException {
        throw new OperationNotSupportedException();
    }
}
