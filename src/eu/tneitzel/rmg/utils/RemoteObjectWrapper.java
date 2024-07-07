package eu.tneitzel.rmg.utils;

import java.rmi.Remote;
import java.rmi.server.RemoteRef;

import eu.tneitzel.rmg.endpoints.KnownEndpoint;
import eu.tneitzel.rmg.endpoints.KnownEndpointHolder;
import eu.tneitzel.rmg.internal.ExceptionHandler;
import sun.rmi.server.UnicastRef;

/**
 * The RemoteObjectWrapper class represents a wrapper around the ordinary RMI remote object related classes.
 * It stores the basic information that is required to use the remote object as usual, but adds additional
 * fields that allow to obtain meta information more easily.
 *
 * From remote-method-guesser v4.3.0 on, the class gets extended by the UnicastWrapper and ActivatableWrapper
 * classes. As the names suggest, UnicastWrapper is used to wrap remote objects that contain a UnicastRef,
 * whereas ActivatableWrapper is used for wrapping ActivatabaseRef types.
 *
 * @author Tobias Neitzel (@qtc_de)
 */
@SuppressWarnings("restriction")
public abstract class RemoteObjectWrapper
{
    /** associated boundName */
    public String boundName;
    /** associated Remote */
    public Remote remoteObject;
    /** associated knownEndpoint */
    public KnownEndpoint knownEndpoint;

    private String interfaceName;

    /**
     * This constructor is only used for special purposes during the enum action. The resulting
     * RemoteObjectWrapper is not fully functional and should not be used for other purposes than
     * displaying the bound name.
     *
     * @param boundName as used in the RMI registry
     */
    public RemoteObjectWrapper(String boundName)
    {
        this.boundName = boundName;
    }

    /**
     * Partially initializes a wrapper. This constructor goes already a little bit deeper than the
     * previous one by also assigning the remote object, checking for the implementing class and
     * whether it is a duplicate. However, this constructor should still only be used by subclasses
     * that add the missing fields.
     *
     * @param boundName bound name as used in the RMI registry
     * @param remoteObject the corresponding remote object
     */
    public RemoteObjectWrapper(String boundName, Remote remoteObject)
    {
        this.boundName = boundName;
        this.remoteObject = remoteObject;

        this.interfaceName = RMGUtils.getInterfaceName(remoteObject);
        this.knownEndpoint = KnownEndpointHolder.getHolder().lookup(interfaceName);
    }

    /**
     * Create a RemoteObjectWrapper for the specified Remote. See the extended
     * version of the function below for more information.
     *
     * @param remote remote to create the wrapper for
     * @return RemoteObjectWrapper for the specified remote
     * @throws IllegalArgumentException if reflective access fails
     * @throws IllegalAccessException if reflective access fails
     * @throws NoSuchFieldException if reflective access fails
     * @throws SecurityException if reflective access fails
     */
    public static RemoteObjectWrapper getInstance(Remote remote) throws IllegalArgumentException, IllegalAccessException, NoSuchFieldException, SecurityException
    {
        return RemoteObjectWrapper.getInstance(remote, null);
    }

    /**
     * Create a RemoteObjectWrapper for the specified Remote. This function uses reflection
     * to inspect the reference type of the specified Remote. If it is a UnicastRef, a UnicastWrapper
     * is returned. Otherwise, an ActivatableWrapper is returned.
     *
     * @param remote remote to create the wrapper for
     * @param boundName bound name as specified in the RMI registry
     * @return RemoteObjectWrapper - Either a UnicastWrapper or a ActivatableWrapper depending on the Remote
     * @throws IllegalArgumentException if reflective access fails
     * @throws IllegalAccessException if reflective access fails
     * @throws NoSuchFieldException if reflective access fails
     * @throws SecurityException if reflective access fails
     */
    public static RemoteObjectWrapper getInstance(Remote remote, String boundName) throws IllegalArgumentException, IllegalAccessException, NoSuchFieldException, SecurityException
    {
        RemoteObjectWrapper wrapper = null;

        RemoteRef ref = RMGUtils.extractRef(remote);
        String interfaceName = RMGUtils.getInterfaceName(remote);

        if (ref instanceof UnicastRef)
        {
            if (interfaceName.equals(SpringRemotingWrapper.invocationHandlerClass))
            {
                wrapper = new SpringRemotingWrapper(remote, boundName, (UnicastRef)ref);
            }

            else
            {
                wrapper = new UnicastWrapper(remote, boundName, (UnicastRef)ref);
            }
        }

        else if (ref.getClass().getName().contains("ActivatableRef"))
        {
            wrapper = new ActivatableWrapper(remote, boundName, ref);
        }

        else
        {
            ExceptionHandler.internalError("RemoteObjectWrapper.getInstance", "Unexpected reference type");
        }

        return wrapper;
    }

    /**
     * Searches a supplied list of RemoteObjectWrapper objects for the Wrapper that is associated to the
     * specified bound name.
     *
     * @param boundName associated bound name to look for
     * @param list RemoteObjectWrapper objects to search in
     * @return RemoteObjectWrapper that matches the specified bound name or null
     */
    public static RemoteObjectWrapper getByName(String boundName, RemoteObjectWrapper[] list)
    {
        for(RemoteObjectWrapper o : list)
        {
            if (o != null && o.boundName.equals(boundName))
            {
                return o;
            }
        }

        return null;
    }

    /**
     * Creates an array of RemoteObjectWrapper from an array of bound names. The resulting RemoteObjectWrappers
     * are dummy objects that just contain the associated bound name. This should only be used during rmg's enum
     * action to display bound names using the Formatter class.
     *
     * @param boundNames Array of String to create the RemoteObjectWrapper from
     * @return Array of RemoteObjectWrapper associated to the specified bound names
     */
    public static RemoteObjectWrapper[] fromBoundNames(String[] boundNames)
    {
        RemoteObjectWrapper[] returnValue = new RemoteObjectWrapper[boundNames.length];

        for(int ctr = 0; ctr < boundNames.length; ctr++)
            returnValue[ctr] = new EmptyWrapper(boundNames[ctr]);

        return returnValue;
    }

    /**
     * Check whether the endpoint is a known endpoint.
     *
     * @return True of the endpoint is known.
     */
    public boolean isKnown()
    {
        if (knownEndpoint == null)
        {
            return false;
        }

        return true;
    }

    /**
     * Return the interface name that is implemented by the remote object.
     *
     * @return interface name implemented by the remote object
     */
    public String getInterfaceName()
    {
        return interfaceName;
    }

    /**
     * Transform an RemoteObjectWrapper into a UnicastWrapper. If the RemoteObjectWrapper is already
     * a UnicastWrapper, it is simply returned. If it is an ActivatableWrapper instead, it is activated.
     *
     * @return UnicastWrapper
     */
    public UnicastWrapper getUnicastWrapper()
    {
        UnicastWrapper returnValue = null;

        if (this instanceof UnicastWrapper)
        {
            returnValue = (UnicastWrapper)this;
        }

        else
        {
            try
            {
                returnValue = ((ActivatableWrapper)this).activate();
            }

            catch (IllegalArgumentException | IllegalAccessException | NoSuchFieldException | SecurityException e)
            {
                ExceptionHandler.unexpectedException(e, "activate", "call", true);
            }
        }

        return returnValue;
    }

    /**
     * Transform an array of RemoteObjectWrapper into an array of UnicastWrapper. If an
     * element is already a UnicastWrapper, it is simply returned. ActivatableWrappers, on
     * the other hand, are activated to create a UnicastWrapper.
     *
     * @param wrappers RemoteObjectWrapper array
     * @return Array of associated UnicastWrappers
     */
    public static UnicastWrapper[] getUnicastWrappers(RemoteObjectWrapper[] wrappers)
    {
        UnicastWrapper[] unicastWrappers = new UnicastWrapper[wrappers.length];

        for (int ctr = 0; ctr < wrappers.length; ctr++)
        {
            if (wrappers[ctr] instanceof UnicastWrapper)
            {
                unicastWrappers[ctr] = (UnicastWrapper)wrappers[ctr];
            }

            else
            {
                try
                {
                    unicastWrappers[ctr] = ((ActivatableWrapper)wrappers[ctr]).activate();
                }

                catch (IllegalArgumentException | IllegalAccessException | NoSuchFieldException | SecurityException e)
                {
                    ExceptionHandler.unexpectedException(e, "activate", "call", true);
                }
            }
        }

        return unicastWrappers;
    }
}
