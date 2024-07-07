package eu.tneitzel.rmg.plugin;

import java.io.File;
import java.lang.reflect.Array;
import java.rmi.Remote;
import java.util.Collection;
import java.util.Map;
import java.util.Map.Entry;

import eu.tneitzel.rmg.internal.ExceptionHandler;
import eu.tneitzel.rmg.io.Logger;
import eu.tneitzel.rmg.utils.ActivatableWrapper;
import eu.tneitzel.rmg.utils.RemoteObjectWrapper;
import eu.tneitzel.rmg.utils.SpringRemotingWrapper;
import eu.tneitzel.rmg.utils.UnicastWrapper;

/**
 * GenericPrint is an rmg ResponseHandler plugin that attempts to print all incoming
 * server responses. It compares the incoming object to some known data types and chooses
 * reasonable defaults to visualize them.
 *
 * From remote-method-guesser v5.0.0, GenericPrint is included per default and does not need
 * to be compiled separately. It can be enabled by using the --show-response option.
 *
 * @author Tobias Neitzel (@qtc_de)
 */
public class GenericPrint implements IResponseHandler
{
    /**
     * The handleResponse function is called with the incoming responseObject from the
     * RMI server. Depending on the corresponding class, a different print action is
     * chosen.
     *
     * @param responseObject Incoming object from an RMI server response
     */
    public void handleResponse(Object responseObject)
    {
        Class<?> responseClass = responseObject.getClass();

        if(responseObject instanceof Collection<?>)
        {
            handleCollection(responseObject);
        }

        else if(responseObject instanceof Map<?,?>)
        {
            handleMap(responseObject);
        }

        else if(responseClass.isArray())
        {
            handleArray(responseObject);
        }

        else if(Remote.class.isAssignableFrom(responseClass))
        {
            handleRemote((Remote)responseObject);
        }

        else if(responseObject instanceof File)
        {
            handleFile((File)responseObject);
        }

        else if(responseObject instanceof Byte)
        {
            handleByte((byte)responseObject);
        }

        else
        {
            handleDefault(responseObject);
        }
    }

    /**
     * For each item within an collection, call handleResponse on the corresponding
     * item value.
     *
     * @param o Object of the Collection type
     */
    public void handleCollection(Object o)
    {
        for(Object item: (Collection<?>)o)
        {
            handleResponse(item);
        }
    }

    /**
     * For each entry within a map, handleResponse is called on the entry key and value.
     * Furthermore, an arrow is printed in an attempt to visualize their relationship.
     *
     * @param o Object of the Map type
     */
    public void handleMap(Object o)
    {
        Map<?,?> map = (Map<?,?>)o;

        for(Entry<?,?> item: map.entrySet())
        {
            handleResponse(item.getKey());
            System.out.print("  --> ");
            handleResponse(item.getValue());
        }
    }

    /**
     * For each item within an array, call the handleResponse function.
     *
     * @param o Object of the Array type
     */
    public void handleArray(Object o)
    {
        Object[] objectArray = null;
        Class<?> type = o.getClass().getComponentType();

        if(type.isPrimitive())
        {
            int length = Array.getLength(o);
            objectArray = new Object[length];

            for(int ctr = 0; ctr < length; ctr++)
            {
                objectArray[ctr] = Array.get(o, ctr);
            }
        }

        else
        {
            objectArray = (Object[])o;
        }

        for(Object item: objectArray)
        {
            handleResponse(item);
        }
    }

    /**
     * For all objects that extend Remote, the details of the remote reference are printed.
     * This includes the class name, the remote TCP endpoint, the assigned ObjID and the
     * configured socket factories.
     *
     * @param o Object that extends the Remote type
     */
    public void handleRemote(Remote o)
    {
        try
        {
            RemoteObjectWrapper objectWrapper = RemoteObjectWrapper.getInstance(o);

            if ((objectWrapper instanceof UnicastWrapper) || objectWrapper instanceof SpringRemotingWrapper)
            {
                UnicastWrapper wrapper = (UnicastWrapper)objectWrapper;

                String csf = "default";
                String ssf = "default";

                if (wrapper.csf != null)
                {
                    csf = wrapper.csf.getClass().getName();
                }

                if (wrapper.ssf != null)
                {
                    ssf = wrapper.ssf.getClass().getName();
                }

                Logger.printlnYellow("Printing unicast RemoteObject:");
                Logger.increaseIndent();
                Logger.printlnMixedBlue("Remote Class:\t\t", wrapper.getInterfaceName());
                Logger.printlnMixedBlue("Endpoint:\t\t", wrapper.getTarget());
                Logger.printlnMixedBlue("ObjID:\t\t\t", wrapper.objID.toString());
                Logger.printlnMixedBlue("ClientSocketFactory:\t", csf);
                Logger.printlnMixedBlue("ServerSocketFactory:\t", ssf);
            }

            else if (objectWrapper instanceof ActivatableWrapper)
            {
                ActivatableWrapper wrapper = (ActivatableWrapper)objectWrapper;

                Logger.printlnYellow("Printing activatable RemoteObject:");
                Logger.increaseIndent();
                Logger.printlnMixedBlue("Remote Class:\t\t", wrapper.getInterfaceName());
                Logger.printlnMixedBlue("Activator:\t\t", wrapper.getActivatorEndpoint());
                Logger.printlnMixedBlue("ActivationID:\t\t", wrapper.activationUID.toString());
            }

            else
            {
                Logger.eprintlnYellow("Unsupported object type.");
            }
        }

        catch (Exception e)
        {
            ExceptionHandler.unexpectedException(e, "constructing", "RemoteObjectWrapper", true);
        }

        finally
        {
            Logger.decreaseIndent();
        }
    }

    /**
     * For File objects, print their absolute path.
     *
     * @param o File object
     */
    public void handleFile(File o)
    {
        Logger.println(o.getAbsolutePath());
    }

    /**
     * Byte objects are converted to hex and printed. As a single byte is most likely part of a
     * sequence, we print without a newline.
     *
     * @param o File object
     */
    public void handleByte(byte o)
    {
        Logger.printPlain(String.format("%02x", o));
    }

    /**
     * The default action for each object is to print it using it's toString method.
     *
     * @param o Object that did not matched one of the previously mentioned types.
     */
    public void handleDefault(Object o)
    {
        Logger.println(o.toString());
    }
}
