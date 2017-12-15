package org.hy.common.xml;

import java.io.Serializable;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.hy.common.Help;
import org.hy.common.MethodReflect;
import org.hy.common.db.DBSQL;
import org.hy.common.xml.annotation.Xsql;
import org.hy.common.xml.plugins.XSQLGroup;
import org.hy.common.xml.plugins.XSQLGroupResult;





/**
 * XSQL代理。
 * 
 * 详见 @see org.hy.common.xml.annotation.Xsql
 *
 * @author      ZhengWei(HY)
 * @createDate  2017-12-14
 * @version     v1.0
 */
public class XSQLProxy implements InvocationHandler ,Serializable
{

    private static final long serialVersionUID = -4219520889151933542L;
    
    /** 代理的接口类 */
    private final Class<?>          xsqlInterface;
    
    /** 
     * 代理接口的实现类。
     * 
     * 当相同XJava的ID同时在接口类及接口的实现类上标记时，此属性才生效。
     * 
     * 类似于JDK 9版本中的新特性：私有接口方法，不过此方法还可以public的。
     * 
     * 可实现如下新奇功能：
     *    接口中被@Xsql注释的方法用代理实现。
     *    同时，接口的实现类，也可实现其中未被@Xsql注释的方法。
     */
    private Object                  xsqlInstace;
    
    /**
     * Map.key    为Java方法对象
     * Map.value  为Java方法对象上的@Xsql注释信息 
     */
    private final Map<Method ,Xsql> methods;
    
    
    
    public static Object newInstace(Class<?> i_XSQLInterface)
    {
        return newInstace(i_XSQLInterface ,null);
    }
    
    
    
    public static Object newInstace(Class<?> i_XSQLInterface ,Object i_XSQLInstace)
    {
        XSQLProxy v_XSQLProxy = new XSQLProxy(i_XSQLInterface);
        
        v_XSQLProxy.setXsqlInstace(i_XSQLInstace);
        
        return Proxy.newProxyInstance(i_XSQLInterface.getClassLoader() ,new Class[]{i_XSQLInterface} ,v_XSQLProxy);
    }
    
    
    
    public XSQLProxy(Class<?> i_XSQLInterface)
    {
        this.xsqlInterface     = i_XSQLInterface;
        this.methods           = new HashMap<Method ,Xsql>();
        List<Method> v_Methods = MethodReflect.getAnnotationMethods(i_XSQLInterface ,Xsql.class);
        
        if ( !Help.isNull(v_Methods) )
        {
            for (Method v_Method : v_Methods)
            {
                this.methods.put(v_Method ,v_Method.getAnnotation(Xsql.class));
            }
        }
    }
    
    

    @Override
    public Object invoke(Object i_Proxy ,Method i_Method ,Object [] i_Args) throws Throwable
    {
        if ( Object.class.equals(i_Method.getDeclaringClass()) )
        {
            return i_Method.invoke(this ,i_Args);
        }
        else
        {
            Xsql v_Xsql = this.methods.get(i_Method);
            
            if ( v_Xsql == null )
            {
                return i_Method.invoke(this ,i_Args);
            }
            // 方法入参个数大于1，应设置@Xsql(paramNames)
            else if ( Help.isNull(v_Xsql.names()) && i_Method.getParameterTypes().length >= 2 )
            {
                return errorLog(i_Method ,"Method parameter count >= 2 ,but @Xsql(paramNames) count is 0.");
            }
            // @Xsql中设置的参数名称个数，应于方法入参个数数量相同
            else if ( !Help.isNull(v_Xsql.names()) 
                   && v_Xsql.names().length > i_Method.getParameterTypes().length )
            {
                return errorLog(i_Method ,"@Xsql(paramNames) count greater than method parameter count.");
            }
            else
            {
                String v_XID     = Help.NVL(v_Xsql.id() ,Help.NVL(v_Xsql.value() ,i_Method.getName()));
                Object v_XObject = XJava.getObject(v_XID);
                
                if ( v_XObject == null )
                {
                    return errorLog(i_Method ,"XID [" + v_XID + "] is not exists.");
                }
                else if ( v_XObject instanceof XSQL )
                {
                    return executeXSQL(i_Method ,v_Xsql ,(XSQL)v_XObject ,i_Args);
                }
                else if ( v_XObject instanceof XSQLGroup )
                {
                    return executeXSQLGroup(i_Method ,v_Xsql ,(XSQLGroup)v_XObject ,i_Args);
                }
                else
                {
                    return errorLog(i_Method ,"XID [" + v_XID + "] java class type is not XSQL or XSQLGroup.");
                }
            }
        }
    }
    
    
    
    /**
     * 异常日志。同时返回执行方法的返回值
     * 
     * @author      ZhengWei(HY)
     * @createDate  2017-12-15
     * @version     v1.0
     *
     * @param i_Method
     * @param i_ErrorInfo
     * @return
     */
    private Object errorLog(Method i_Method ,String i_ErrorInfo)
    {
        System.err.println("Call " + this.xsqlInterface.getName() + "." + i_Method.getName() + "：" + i_ErrorInfo);
        
        // 定义的方法无返回类型：void
        if ( Void.TYPE == i_Method.getReturnType() )
        {
            return null;
        }
        // 返回执行成功与否标记
        else if ( Boolean.class == i_Method.getReturnType() 
               || boolean.class == i_Method.getReturnType() )
        {
            return Boolean.FALSE;
        }
        else
        {
            return null;
        }
    }
    
    
    
    /**
     * 执行XSQL
     * 
     * @author      ZhengWei(HY)
     * @createDate  2017-12-15
     * @version     v1.0
     *
     * @param i_Method
     * @param i_Xsql
     * @param i_XSQL
     * @param i_Args
     * @return
     */
    private Object executeXSQL(Method i_Method ,Xsql i_Xsql ,XSQL i_XSQL ,Object [] i_Args)
    {
        if ( i_XSQL.getContentDB().getSQLType() == DBSQL.$DBSQL_TYPE_SELECT )
        {
            return executeXSQL_Query(i_Method ,i_Xsql ,i_XSQL ,i_Args);
        }
        else if ( i_XSQL.getContentDB().getSQLType() == DBSQL.$DBSQL_TYPE_INSERT
               || i_XSQL.getContentDB().getSQLType() == DBSQL.$DBSQL_TYPE_UPDATE
               || i_XSQL.getContentDB().getSQLType() == DBSQL.$DBSQL_TYPE_DELETE )
        {
            return executeXSQL_ExecuteUpdate(i_Method ,i_Xsql ,i_XSQL ,i_Args);
        }
        else if ( i_XSQL.getContentDB().getSQLType() == DBSQL.$DBSQL_TYPE_DDL )
        {
            return executeXSQL_Execute(i_Method ,i_Xsql ,i_XSQL ,i_Args);
        }
        else if ( i_XSQL.getContentDB().getSQLType() == DBSQL.$DBSQL_TYPE_CALL )
        {
            return executeXSQL_Call(i_Method ,i_Xsql ,i_XSQL ,i_Args);
        }
        else if ( i_XSQL.getContentDB().getSQLType() == DBSQL.$DBSQL_TYPE_UNKNOWN )
        {
            return executeXSQL_Execute(i_Method ,i_Xsql ,i_XSQL ,i_Args);
        }
        
        return null;
    }
    
    
    
    /**
     * 执行XSQL -- 查询
     * 
     * @author      ZhengWei(HY)
     * @createDate  2017-12-15
     * @version     v1.0
     *
     * @param i_Method
     * @param i_Xsql
     * @param i_XSQL
     * @param i_Args
     * @return
     */
    private Object executeXSQL_Query(Method i_Method ,Xsql i_Xsql ,XSQL i_XSQL ,Object [] i_Args)
    {
        Object v_Params = getExecuteParams(i_Xsql ,i_Args);
        Object v_Ret    = null;
        
        if ( i_Args == null || i_Args.length == 0 )
        {
            v_Ret = i_XSQL.query();
        }
        else
        {
            v_Ret = i_XSQL.query(v_Params);
        }
        
        if ( v_Ret != null )
        {
            if ( Void.TYPE == i_Method.getReturnType() )
            {
                return null;
            }
            
            if ( i_Xsql.returnOne() )
            {
                if ( MethodReflect.isExtendImplement(v_Ret ,List.class) )
                {
                    List<?> v_List = (List<?>)v_Ret;
                    
                    if ( v_List.size() >= 1 )
                    {
                        return v_List.get(0);
                    }
                }
            }
            
            return v_Ret;
        }
        else
        {
            return null;
        }
    }
    
    
    
    /**
     * 执行XSQL
     * 
     * @author      ZhengWei(HY)
     * @createDate  2017-12-15
     * @version     v1.0
     *
     * @param i_Method
     * @param i_Xsql
     * @param i_XSQL
     * @param i_Args
     * @return
     */
    private Object executeXSQL_ExecuteUpdate(Method i_Method ,Xsql i_Xsql ,XSQL i_XSQL ,Object [] i_Args)
    {
        Object v_Params = getExecuteParams(i_Xsql ,i_Args);
        int    v_Ret    = -1;
        
        if ( i_Args == null || i_Args.length == 0 )
        {
            v_Ret = i_XSQL.executeUpdate();
        }
        else
        {
            v_Ret = i_XSQL.executeUpdate(v_Params);
        }
        
        if ( Void.TYPE == i_Method.getReturnType() )
        {
            return null;
        }
        else if ( Boolean.class == i_Method.getReturnType() 
               || boolean.class == i_Method.getReturnType() )
        {
            return v_Ret >= 1;
        }
        else if ( Integer.class == i_Method.getReturnType() 
               ||     int.class == i_Method.getReturnType() )
        {
            return v_Ret;
        }
        else
        {
            return null;
        }
    }
    
    
    
    /**
     * 执行XSQL
     * 
     * @author      ZhengWei(HY)
     * @createDate  2017-12-15
     * @version     v1.0
     *
     * @param i_Method
     * @param i_Xsql
     * @param i_XSQL
     * @param i_Args
     * @return
     */
    private Boolean executeXSQL_Execute(Method i_Method ,Xsql i_Xsql ,XSQL i_XSQL ,Object [] i_Args)
    {
        Object  v_Params = getExecuteParams(i_Xsql ,i_Args);
        boolean v_Ret    = false;
        
        if ( i_Args == null || i_Args.length == 0 )
        {
            v_Ret = i_XSQL.execute();
        }
        else
        {
            v_Ret = i_XSQL.execute(v_Params);
        }
        
        if ( Void.TYPE == i_Method.getReturnType() )
        {
            return null;
        }
        else if ( Boolean.class == i_Method.getReturnType() 
               || boolean.class == i_Method.getReturnType() )
        {
            return v_Ret;
        }
        else
        {
            return null;
        }
    }
    
    
    
    /**
     * 执行XSQL
     * 
     * @author      ZhengWei(HY)
     * @createDate  2017-12-15
     * @version     v1.0
     *
     * @param i_Method
     * @param i_Xsql
     * @param i_XSQL
     * @param i_Args
     * @return
     */
    private Object executeXSQL_Call(Method i_Method ,Xsql i_Xsql ,XSQL i_XSQL ,Object [] i_Args)
    {
        Object v_Params = getExecuteParams(i_Xsql ,i_Args);
        Object v_Ret    = null;
        
        if ( i_Args == null || i_Args.length == 0 )
        {
            v_Ret = i_XSQL.call();
        }
        else
        {
            v_Ret = i_XSQL.call(v_Params);
        }
        
        if ( Void.TYPE == i_Method.getReturnType() )
        {
            return null;
        }
        else
        {
            return v_Ret;
        }
    }
    
    
    
    /**
     * 执行XSQL组
     * 
     * @author      ZhengWei(HY)
     * @createDate  2017-12-15
     * @version     v1.0
     *
     * @param i_Method
     * @param i_Xsql
     * @param i_XSQLGroup
     * @param i_Args
     * @return
     */
    private Object executeXSQLGroup(Method i_Method ,Xsql i_Xsql ,XSQLGroup i_XSQLGroup ,Object [] i_Args)
    {
        Object          v_Params = getExecuteParams(i_Xsql ,i_Args);
        XSQLGroupResult v_Ret    = null;
        
        if ( i_Args == null || i_Args.length == 0 )
        {
            v_Ret = i_XSQLGroup.executes();
        }
        else
        {
            v_Ret = i_XSQLGroup.executes(v_Params);
        }
        
        // 异常时输出执行日志
        if ( !v_Ret.isSuccess() )
        {
            i_XSQLGroup.logReturn(v_Ret);
        }
        
        // 定义的方法无返回类型：void
        if ( Void.TYPE == i_Method.getReturnType() )
        {
            return null;
        }
        // 返回指定ID的数据结果集
        else if ( !Help.isNull(i_Xsql.returnID()) )
        {
            if ( v_Ret.isSuccess() )
            {
                return v_Ret.getReturns().get(i_Xsql.returnID());
            }
            else
            {
                return null;
            }
        }
        // 返回多个数据结果集
        else if ( Map.class == i_Method.getReturnType() )
        {
            if ( v_Ret.isSuccess() )
            {
                return v_Ret.getReturns();
            }
            else
            {
                return null;
            }
        }
        // 返回执行成功与否标记
        else if ( Boolean.class == i_Method.getReturnType() 
               || boolean.class == i_Method.getReturnType() )
        {
            return v_Ret.isSuccess();
        }
        // 返回XSQL组的执行结果。可由外部控制提交、回滚等操作，及多个数据结果集的返回。
        else if ( XSQLGroupResult.class == i_Method.getReturnType() )
        {
            return v_Ret;
        }
        // 默认返回XSQL组的执行结果
        else if ( Object.class == i_Method.getReturnType() )
        {
            return v_Ret;
        }
        else
        {
            return null;
        }
    }
    
    
    
    /**
     * 获取并生成执行参数。
     * 
     * 当方法入参个数大于1时，需要整合成一个Map集合。
     * 
     * 详见 @Xsql.paramNames
     * 
     * @author      ZhengWei(HY)
     * @createDate  2017-12-15
     * @version     v1.0
     *
     * @param i_Xsql
     * @param i_Args
     * @return
     */
    private Object getExecuteParams(Xsql i_Xsql ,Object [] i_Args)
    {
        if ( Help.isNull(i_Xsql.names()) )
        {
            if ( i_Args == null || i_Args.length == 0 )
            {
                return null;
            }
            else
            {
                return i_Args[0];
            }
        }
        else
        {
            Map<String ,Object> v_Params = new HashMap<String ,Object>();
            for (int v_PIndex=0; v_PIndex<i_Xsql.names().length; v_PIndex++)
            {
                String v_ParamName = i_Xsql.names()[v_PIndex];
                if ( !Help.isNull(v_ParamName) )
                {
                    v_Params.put(v_ParamName ,i_Args[v_PIndex]);
                }
            }
            
            return v_Params;
        }
    }


    
    /**
     * 获取：代理接口的实现类。
     * 
     * 当相同XJava的ID同时在接口类及接口的实现类上标记时，此属性才生效。
     * 
     * 类似于JDK 9版本中的新特性：私有接口方法，不过此方法还可以public的。
     * 
     * 可实现如下新奇功能：
     *    接口中被@Xsql注释的方法用代理实现。
     *    同时，接口的实现类，也可实现其中未被@Xsql注释的方法。
     */
    public Object getXsqlInstace()
    {
        return xsqlInstace;
    }
    

    
    /**
     * 设置：代理接口的实现类。
     * 
     * 当相同XJava的ID同时在接口类及接口的实现类上标记时，此属性才生效。
     * 
     * 类似于JDK 9版本中的新特性：私有接口方法，不过此方法还可以public的。
     * 
     * 可实现如下新奇功能：
     *    接口中被@Xsql注释的方法用代理实现。
     *    同时，接口的实现类，也可实现其中未被@Xsql注释的方法。
     * 
     * @param xsqlInstace 
     */
    public void setXsqlInstace(Object xsqlInstace)
    {
        this.xsqlInstace = xsqlInstace;
    }

}
