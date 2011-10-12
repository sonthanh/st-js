package org.stjs.generator.scope.simple;

import japa.parser.ast.CompilationUnit;
import japa.parser.ast.ImportDeclaration;
import japa.parser.ast.TypeParameter;
import japa.parser.ast.body.BodyDeclaration;
import japa.parser.ast.body.ClassOrInterfaceDeclaration;
import japa.parser.ast.body.ConstructorDeclaration;
import japa.parser.ast.body.EnumDeclaration;
import japa.parser.ast.body.MethodDeclaration;
import japa.parser.ast.body.Parameter;
import japa.parser.ast.body.VariableDeclarator;
import japa.parser.ast.expr.Expression;
import japa.parser.ast.expr.NameExpr;
import japa.parser.ast.expr.ObjectCreationExpr;
import japa.parser.ast.expr.QualifiedNameExpr;
import japa.parser.ast.expr.VariableDeclarationExpr;
import japa.parser.ast.stmt.CatchClause;
import japa.parser.ast.stmt.ForStmt;
import japa.parser.ast.stmt.ForeachStmt;
import japa.parser.ast.type.ClassOrInterfaceType;
import japa.parser.ast.type.PrimitiveType;
import japa.parser.ast.type.ReferenceType;
import japa.parser.ast.type.Type;
import japa.parser.ast.type.VoidType;
import japa.parser.ast.type.WildcardType;
import japa.parser.ast.visitor.VoidVisitorAdapter;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.stjs.generator.scope.classloader.ClassLoaderWrapper;
import org.stjs.generator.scope.classloader.ClassWrapper;
import org.stjs.generator.utils.Option;

import com.google.common.base.Function;
import com.google.common.collect.MapMaker;

public class SimpleScopeBuilder extends VoidVisitorAdapter<Scope> {

	private final ClassLoaderWrapper classLoader;
	
	private final Map<ClassScope, AtomicInteger> anonymousClassCount = 
		new MapMaker().makeComputingMap(new Function<ClassScope, AtomicInteger>() {

			@Override
			public AtomicInteger apply(ClassScope input) {
				return new AtomicInteger(0);
			}
	});

	public SimpleScopeBuilder(ClassLoaderWrapper classLoader) {
		this.classLoader = classLoader;
	}

	@Override
	public void visit(final CompilationUnit n, Scope scope) {
		scope.apply(new DefaultScopeVisitor() {
			public void apply(CompilationUnitScope scope) {
				// asterisk declaration have lower priority => process them first (JLS §7.5.2)
				if (n.getImports() != null) {
					for (ImportDeclaration importDecl : n.getImports()) {
						NameExpr name = importDecl.getName();
						checkImport(importDecl, name.toString());
						if (importDecl.isAsterisk()) {
							if (importDecl.isStatic()) {
								QualifiedNameExpr expr = (QualifiedNameExpr) name;
								for (ClassWrapper clazz : identifyQualifiedNameExprClass(expr.getQualifier())) {
									for (Field field : clazz.getDeclaredNonPrivateStaticFields()) {
										scope.addField(field);
									}
									for (Method method : clazz.getDeclaredNonPrivateStaticMethods()) {
										scope.addMethod(method);
									}
									for (ClassWrapper type : clazz.getDeclaredNonPrivateStaticClasses()) {
										scope.addType(type);
									}
								}
							} else {
								scope.addTypeImportOnDemand(name);
							}
						}
					}
					for (ImportDeclaration importDecl : n.getImports()) {
						NameExpr name = importDecl.getName();
						checkImport(importDecl, name.toString());
						if (!importDecl.isAsterisk()) {
							if (importDecl.isStatic()) {
								QualifiedNameExpr expr = (QualifiedNameExpr) name;
								for (ClassWrapper clazz : identifyQualifiedNameExprClass(expr.getQualifier())) {
									String fieldOrTypeOrMethodName = name.getName();
									for (Field field : clazz.getDeclaredField(fieldOrTypeOrMethodName)) {
										scope.addField(field);
									}
									List<Method> methods = clazz.getDeclaredMethods(fieldOrTypeOrMethodName);
									if (!methods.isEmpty()) {
										scope.addMethods(fieldOrTypeOrMethodName, methods);
									}
									for (ClassWrapper innerClass : clazz.getDeclaredClass(fieldOrTypeOrMethodName)) {
										scope.addType(innerClass);
										break;
									}
									// TODO : do wee need to continue here?
								}
							}
							for (ClassWrapper clazz : identifyQualifiedNameExprClass(name)) {
								scope.addType(clazz);
								
							}
						}
					}
				}
			}
		});
		
		super.visit(n, scope);
	}
	
	@Override
	public void visit(final ClassOrInterfaceDeclaration n, Scope scope) {
		if ((n.getExtends() != null) && (n.getExtends().size() > 0) && !n.isInterface()) {
			// TODO : populate scope with parent
		}
		final AtomicReference<Scope> classScope = new AtomicReference<Scope>();
		scope.apply(new DefaultScopeVisitor() {
			
			@Override
			public void apply(CompilationUnitScope compilationUnitScope) {
				// TODO : this is not good enough for inner classes (which need the list of outer classes in their qualified name)
				String qualifiedName = compilationUnitScope.getPackage().getName().toString()+"."+n.getName();
				if (!n.isInterface()) {
					ClassWrapper clazz = classLoader.loadClassOrInnerClass(qualifiedName).getOrThrow("Cannot load class or interface "+qualifiedName);
					compilationUnitScope.addType(clazz);
					if (n.getTypeParameters() != null) {
						for (TypeParameter p : n.getTypeParameters()) {
							// TODO : Handle Generic Types definitions
						}
					}
					for (ClassWrapper innerClass : clazz.getDeclaredClasses()) {
						compilationUnitScope.addType(innerClass);
					}
					ClassScope scope = new ClassScope(clazz, compilationUnitScope);
					for (Field field : clazz.getDeclaredFields()) {
						scope.addField(field);
					}
					for (Method method : clazz.getDeclaredMethods()) {
						scope.addMethod(method);
					}
					classScope.set(scope);
				}
			}

			@Override
			public void apply(ClassScope classScope) {
				// NOTE : static class must inherit the compilation unit scope, non static class the class scope
			//	throw new RuntimeException("Inner class not implemented yet");
			}
		});
		super.visit(n, classScope.get());
	}


	@Override
	public void visit(final MethodDeclaration n, Scope currentScope) {
		BasicScope scope = handleMethodDeclaration(n.getParameters(), currentScope);
		super.visit(n, new BasicScope(scope));
	}

	private BasicScope handleMethodDeclaration(final List<Parameter> parameters, Scope currentScope) {

		BasicScope scope = new BasicScope((ClassScope) currentScope);
		if (parameters != null) {
			for (Parameter p : parameters) {
				ClassWrapper clazz = resolveType(scope, p.getType());
				scope.addVariable(new ParameterVariable(clazz, p.getId().getName()));
			}
		}
		// if (n.getTypeParameters() != null) {
		// for (TypeParameter p : n.getTypeParameters()) {
		// // TODO : Generics
		// }
		// }
		return scope;
	}


	private ClassWrapper resolveType(Scope scope, Type type) {
		// TODO : shouldn't that go directly in the scope classes?
		if (type instanceof ReferenceType) {
			ReferenceType refType = (ReferenceType) type;
			if (refType.getArrayCount() > 0) {
				throw new RuntimeException("Arrays are not supported");
			}
			type = refType.getType(); // type is a primitive or class
		}
		if (type instanceof PrimitiveType) {
			PrimitiveType primitiveType = (PrimitiveType) type;
			return PrimitiveTypes.primitiveReflectionType(primitiveType);
		} else if (type instanceof VoidType) {
			return new ClassWrapper(void.class);
		} else if (type instanceof ClassOrInterfaceType) {
			return  scope.resolveType(type.toString());
		} else if (type instanceof WildcardType) {
			throw new RuntimeException("Generics not yet implemented");
		} else {
			throw new RuntimeException("Unpexcted type " + type);
		}
	}

	@Override
	public void visit(VariableDeclarationExpr n, Scope scope) {
		BasicScope basicScope = (BasicScope) scope;
		if (n.getVars() != null) {
			ClassWrapper clazz = resolveType(basicScope, n.getType());
			/* TODO : this is not as simple. the order of the variables declarations matters!
			 In this example 
			 class XXX {
			 	private String x;
				void m() {
					String y = x;
					String x = "hello";
					String k = x;
				}
			}
			
			y = x refers to the field but k = x refers to the local var.
			if you replace String x = "hello", by String x = x; this causes a compilation error,
			because it is equivalent to;
			String x;
			x=x;
			This means that 'String x;' create a new scope in which ' = x' is evaluated.  
			*/
			for (VariableDeclarator var : n.getVars()) {
				basicScope.addVariable(new LocalVariable(clazz, var.getId().getName()));
			}
		}

		super.visit(n, scope);
	}

	@Override
	public void visit(CatchClause n, Scope currentScope) {
		// TODO : this is broken in Java7 because a catch block might declare more than one exception
		// would need a new javap
		BasicScope scope = new BasicScope(currentScope);
		scope.addVariable(new ParameterVariable(resolveType(scope, n.getExcept().getType()), n.getExcept().getId().getName()));
		super.visit(n, scope);
	}
	
	@Override
	public void visit(ConstructorDeclaration n, Scope currentScope) {
		BasicScope scope = handleMethodDeclaration(n.getParameters(), currentScope);
		super.visit(n, new BasicScope(scope));
	}
	
	@Override
	public void visit(ForeachStmt n, Scope currentScope) {
		super.visit(n, new BasicScope(currentScope));
	}

	@Override
	public void visit(ForStmt n, Scope currentScope) {
		super.visit(n, new BasicScope(currentScope));
	}
	
	@Override
	public void visit(final EnumDeclaration n, final Scope currentScope) {
		ClassScope parentClassScope = (ClassScope) currentScope;
		ClassWrapper parentClass = parentClassScope.getClazz();
		ClassWrapper enumClass = classLoader.loadClass(parentClass.getName() + "$" + n.getName()).getOrThrow();
		ClassScope enumClassScope = new ClassScope(enumClass, currentScope);
		for (ClassWrapper innerClass : enumClass.getDeclaredClasses()) {
			/*
			 * TODO : that's maybe not correct. Need to check what's the diff between static and non static inner
			 * classes of enumrations
			 */
			enumClassScope.addType(innerClass);
		}
		for (Field field : enumClass.getDeclaredFields()) {
			enumClassScope.addField(field);
		}
		for (Method method : enumClass.getDeclaredMethods()) {
			enumClassScope.addMethod(method);
		}
		super.visit(n, enumClassScope);
		
	}


	@Override
	public void visit(ObjectCreationExpr n, Scope scope) {
		 if (n.getScope() != null) {
	            n.getScope().accept(this, scope);
	        }
	        if (n.getTypeArgs() != null) {
	            for (Type t : n.getTypeArgs()) {
	                t.accept(this, scope);
	            }
	        }
	        n.getType().accept(this, scope);
	        if (n.getArgs() != null) {
	            for (Expression e : n.getArgs()) {
	                e.accept(this, scope);
	            }
	        }
	        if (n.getAnonymousClassBody() != null) {
	        	ClassScope classScope = scope.closest(ClassScope.class);
	        	int anonymousClassNumber = anonymousClassCount.get(classScope).incrementAndGet();
	        	ClassWrapper anonymousClass = classLoader.loadClass(classScope.getClazz().getName()+"$"+anonymousClassNumber).getOrThrow();
	        	ClassScope anonymousClassScope = new ClassScope(anonymousClass, scope);
				for (ClassWrapper innerClass : anonymousClass.getDeclaredClasses()) {
					anonymousClassScope.addType(innerClass);
				}
				for (Field field : anonymousClass.getDeclaredFields()) {
					anonymousClassScope.addField(field);
				}
				for (Method method : anonymousClass.getDeclaredMethods()) {
					anonymousClassScope.addMethod(method);
				}
				
	        	for (BodyDeclaration member : n.getAnonymousClassBody()) {
	            	member.accept(this, anonymousClassScope);
	            }
	        }
	}
	

	
	private Option<ClassWrapper> identifyQualifiedNameExprClass(NameExpr expr) {
		return classLoader.loadClassOrInnerClass(expr.toString());
	}


	private void checkImport(ImportDeclaration importDecl, String string) {
		// TODO Auto-generated method stub

	}
}