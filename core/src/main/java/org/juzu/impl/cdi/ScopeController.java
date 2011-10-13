package org.juzu.impl.cdi;

import org.juzu.ActionScoped;
import org.juzu.RenderScoped;
import org.juzu.impl.request.RequestContext;
import org.juzu.impl.request.Scope;

import javax.enterprise.context.ContextNotActiveException;
import javax.enterprise.context.RequestScoped;
import javax.enterprise.context.SessionScoped;
import javax.enterprise.context.spi.Contextual;
import javax.enterprise.context.spi.CreationalContext;
import java.util.Collections;
import java.util.Map;

/** @author <a href="mailto:julien.viet@exoplatform.com">Julien Viet</a> */
public class ScopeController
{

   /** . */
   private static final Map<Contextual<?>, Object> EMPTY_MAP = Collections.emptyMap();

   /** . */
   static final ScopeController INSTANCE = new ScopeController();

   /** . */
   final ContextImpl requestContext = new ContextImpl(this, Scope.REQUEST, RequestScoped.class);

   /** . */
   final ContextImpl actionContext = new ContextImpl(this, Scope.ACTION, ActionScoped.class);

   /** . */
   final ContextImpl renderContext = new ContextImpl(this, Scope.RENDER, RenderScoped.class);

   /** . */
   final ContextImpl sessionContext = new ContextImpl(this, Scope.SESSION, SessionScoped.class);

   /** . */
   final ThreadLocal<RequestContext> currentContext = new ThreadLocal<RequestContext>();

   public static void begin(RequestContext context) throws IllegalStateException
   {
      if (context == null)
      {
         throw new NullPointerException();
      }
      if (INSTANCE.currentContext.get() != null)
      {
         throw new IllegalStateException("Already started");
      }
      INSTANCE.currentContext.set(context);
   }

   public static void end()
   {
      INSTANCE.currentContext.set(null);
   }

   public <T> T get(Scope scope, Contextual<T> contextual, CreationalContext<T> creationalContext)
   {
      RequestContext ctx = currentContext.get();
      if (ctx == null)
      {
         throw new ContextNotActiveException();
      }
      Map<Object, Object> map = ctx.getContext(scope);
      if (map == null)
      {
         throw new ContextNotActiveException();
      }
      Object o = map.get(contextual);
      if (o == null)
      {
         if (creationalContext != null)
         {
            o = contextual.create(creationalContext);
            map.put(contextual, o);
         }
      }
      return (T)o;
   }

   public boolean isActive(Scope scope)
   {
      RequestContext ctx = currentContext.get();
      if (ctx == null)
      {
         throw new ContextNotActiveException();
      }
      return ctx.getContext(scope) != null;
   }
}
